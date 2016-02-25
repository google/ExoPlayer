/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import com.google.android.exoplayer.util.Util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaFormat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Representation of a media format.
 */
public final class Format {

  /**
   * Sorts {@link Format} objects in order of decreasing bandwidth.
   */
  public static final class DecreasingBandwidthComparator implements Comparator<Format> {

    @Override
    public int compare(Format a, Format b) {
      return b.bitrate - a.bitrate;
    }

  }

  public static final int NO_VALUE = -1;

  /**
   * A value for {@link #subsampleOffsetUs} to indicate that subsample timestamps are relative to
   * the timestamps of their parent samples.
   */
  public static final long OFFSET_SAMPLE_RELATIVE = Long.MAX_VALUE;

  /**
   * An identifier for the format, or null if unknown or not applicable.
   */
  public final String id;
  /**
   * The average bandwidth in bits per second, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int bitrate;

  // Container specific.

  /**
   * The mime type of the container, or null if unknown or not applicable.
   */
  public final String containerMimeType;

  // Elementary stream specific.

  /**
   * The mime type of the elementary stream (i.e. the individual samples), or null if unknown or not
   * applicable.
   */
  public final String sampleMimeType;
  /**
   * The maximum size of a buffer of data (typically one sample), or {@link #NO_VALUE} if unknown or
   * not applicable.
   */
  public final int maxInputSize;
  /**
   * Whether the decoder is required to support secure decryption.
   */
  public final boolean requiresSecureDecryption;
  /**
   * Initialization data that must be provided to the decoder. Will not be null, but may be empty
   * if initialization data is not required.
   */
  public final List<byte[]> initializationData;

  // Video specific.

  /**
   * The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int width;
  /**
   * The height of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int height;
  /**
   * The frame rate in frames per second, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final float frameRate;
  /**
   * The clockwise rotation that should be applied to the video for it to be rendered in the correct
   * orientation, or {@link #NO_VALUE} if unknown or not applicable. Only 0, 90, 180 and 270 are
   * supported.
   */
  public final int rotationDegrees;
  /**
   * The width to height ratio of pixels in the video, or {@link #NO_VALUE} if unknown or not
   * applicable.
   */
  public final float pixelWidthHeightRatio;

  // Audio specific.

  /**
   * The number of audio channels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int channelCount;
  /**
   * The audio sampling rate in Hz, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int sampleRate;

  // Text specific.

  /**
   * For samples that contain subsamples, this is an offset that should be added to subsample
   * timestamps. A value of {@link #OFFSET_SAMPLE_RELATIVE} indicates that subsample timestamps are
   * relative to the timestamps of their parent samples.
   */
  public final long subsampleOffsetUs;

  // Audio and text specific.

  /**
   * The language, or null if unknown or not applicable.
   */
  public final String language;

  // Lazily initialized hashcode and framework media format.

  private int hashCode;
  private MediaFormat frameworkMediaFormat;

  // Video.

  public static Format createVideoContainerFormat(String id, String containerMimeType,
      String sampleMimeType, int bitrate, int width, int height, float frameRate,
      List<byte[]> initializationData) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, NO_VALUE, width, height,
        frameRate, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE,
        initializationData, false);
  }

  public static Format createVideoSampleFormat(String id, String sampleMimeType, int bitrate,
      int maxInputSize, int width, int height, float frameRate, List<byte[]> initializationData) {
    return createVideoSampleFormat(id, sampleMimeType, bitrate, maxInputSize, width, height,
        frameRate, initializationData, NO_VALUE, NO_VALUE);
  }

  public static Format createVideoSampleFormat(String id, String sampleMimeType, int bitrate,
      int maxInputSize, int width, int height, float frameRate, List<byte[]> initializationData,
      int rotationDegrees, float pixelWidthHeightRatio) {
    return new Format(id, null, sampleMimeType, bitrate, maxInputSize, width, height, frameRate,
        rotationDegrees, pixelWidthHeightRatio, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE,
        initializationData, false);
  }

  // Audio.

  public static Format createAudioContainerFormat(String id, String containerMimeType,
      String sampleMimeType, int bitrate, int channelCount, int sampleRate,
      List<byte[]> initializationData, String language) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, NO_VALUE, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, channelCount, sampleRate, language, OFFSET_SAMPLE_RELATIVE,
        initializationData, false);
  }

  public static Format createAudioSampleFormat(String id, String sampleMimeType, int bitrate,
      int maxInputSize, int channelCount, int sampleRate, List<byte[]> initializationData,
      String language) {
    return new Format(id, null, sampleMimeType, bitrate, maxInputSize, NO_VALUE, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, channelCount, sampleRate, language, OFFSET_SAMPLE_RELATIVE,
        initializationData, false);
  }

  // Text.

  public static Format createTextContainerFormat(String id, String containerMimeType,
      String sampleMimeType, int bitrate, String language) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, NO_VALUE, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, language, OFFSET_SAMPLE_RELATIVE, null,
        false);
  }

  public static Format createTextSampleFormat(String id, String sampleMimeType, int bitrate,
      String language) {
    return createTextSampleFormat(id, sampleMimeType, bitrate, language, OFFSET_SAMPLE_RELATIVE);
  }

  public static Format createTextSampleFormat(String id, String sampleMimeType, int bitrate,
      String language, long subsampleOffsetUs) {
    return new Format(id, null, sampleMimeType, bitrate, NO_VALUE, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, language, subsampleOffsetUs, null, false);
  }

  // Generic.

  public static Format createContainerFormat(String id, String containerMimeType,
      String sampleMimeType, int bitrate) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, NO_VALUE, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE, null,
        false);
  }

  public static Format createSampleFormat(String id, String sampleMimeType, int bitrate) {
    return new Format(id, null, sampleMimeType, bitrate, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
        NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, OFFSET_SAMPLE_RELATIVE, null, false);
  }

  /* package */ Format(String id, String containerMimeType, String sampleMimeType,
      int bitrate, int maxInputSize, int width, int height, float frameRate, int rotationDegrees,
      float pixelWidthHeightRatio, int channelCount, int sampleRate, String language,
      long subsampleOffsetUs, List<byte[]> initializationData, boolean requiresSecureDecryption) {
    this.id = id;
    this.containerMimeType = containerMimeType;
    this.sampleMimeType = sampleMimeType;
    this.bitrate = bitrate;
    this.maxInputSize = maxInputSize;
    this.width = width;
    this.height = height;
    this.frameRate = frameRate;
    this.rotationDegrees = rotationDegrees;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.language = language;
    this.subsampleOffsetUs = subsampleOffsetUs;
    this.initializationData = initializationData == null ? Collections.<byte[]>emptyList()
        : initializationData;
    this.requiresSecureDecryption = requiresSecureDecryption;
  }

  public Format copyWithMaxInputSize(int maxInputSize) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, maxInputSize, width,
        height, frameRate, rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate,
        language, subsampleOffsetUs, initializationData, requiresSecureDecryption);
  }

  public Format copyWithSubsampleOffsetUs(long subsampleOffsetUs) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, maxInputSize, width,
        height, frameRate, rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate,
        language, subsampleOffsetUs, initializationData, requiresSecureDecryption);
  }

  public Format copyWithContainerInfo(String id, int bitrate, int width, int height,
      String language) {
    return new Format(id, containerMimeType, sampleMimeType, bitrate, maxInputSize, width,
        height, frameRate, rotationDegrees, pixelWidthHeightRatio, channelCount, sampleRate,
        language, subsampleOffsetUs, initializationData, requiresSecureDecryption);
  }

  /**
   * @return A {@link MediaFormat} representation of this format.
   */
  @SuppressLint("InlinedApi")
  @TargetApi(16)
  public final MediaFormat getFrameworkMediaFormatV16() {
    if (frameworkMediaFormat == null) {
      MediaFormat format = new MediaFormat();
      format.setString(MediaFormat.KEY_MIME, sampleMimeType);
      maybeSetStringV16(format, MediaFormat.KEY_LANGUAGE, language);
      maybeSetIntegerV16(format, MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
      maybeSetIntegerV16(format, MediaFormat.KEY_WIDTH, width);
      maybeSetIntegerV16(format, MediaFormat.KEY_HEIGHT, height);
      maybeSetFloatV16(format, MediaFormat.KEY_FRAME_RATE, frameRate);
      maybeSetIntegerV16(format, "rotation-degrees", rotationDegrees);
      maybeSetIntegerV16(format, MediaFormat.KEY_CHANNEL_COUNT, channelCount);
      maybeSetIntegerV16(format, MediaFormat.KEY_SAMPLE_RATE, sampleRate);
      for (int i = 0; i < initializationData.size(); i++) {
        format.setByteBuffer("csd-" + i, ByteBuffer.wrap(initializationData.get(i)));
      }
      frameworkMediaFormat = format;
    }
    return frameworkMediaFormat;
  }

  /**
   * Sets the {@link MediaFormat} returned by {@link #getFrameworkMediaFormatV16()}.
   *
   * @deprecated This method only exists for FrameworkSampleSource, which is itself deprecated.
   * @param frameworkSampleFormat The format.
   */
  @Deprecated
  @TargetApi(16)
  /* package */ final void setFrameworkMediaFormatV16(MediaFormat frameworkSampleFormat) {
    this.frameworkMediaFormat = frameworkSampleFormat;
  }

  @Override
  public String toString() {
    return "Format(" + id + ", " + containerMimeType + ", " + sampleMimeType + ", " + bitrate + ", "
        + maxInputSize + ", " + language + ", [" + width + ", " + height + ", " + frameRate + ", "
        + rotationDegrees + ", " + pixelWidthHeightRatio + "]" + ", [" + channelCount + ", "
        + sampleRate + "])";
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + (containerMimeType == null ? 0 : containerMimeType.hashCode());
      result = 31 * result + (sampleMimeType == null ? 0 : sampleMimeType.hashCode());
      result = 31 * result + bitrate;
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + (language == null ? 0 : language.hashCode());
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Format other = (Format) obj;
    if (bitrate != other.bitrate || maxInputSize != other.maxInputSize
        || requiresSecureDecryption != other.requiresSecureDecryption
        || width != other.width || height != other.height || frameRate != other.frameRate
        || rotationDegrees != other.rotationDegrees
        || pixelWidthHeightRatio != other.pixelWidthHeightRatio
        || channelCount != other.channelCount || sampleRate != other.sampleRate
        || subsampleOffsetUs != other.subsampleOffsetUs
        || !Util.areEqual(id, other.id) || !Util.areEqual(language, other.language)
        || !Util.areEqual(containerMimeType, other.containerMimeType)
        || !Util.areEqual(sampleMimeType, other.sampleMimeType)
        || initializationData.size() != other.initializationData.size()) {
      return false;
    }
    for (int i = 0; i < initializationData.size(); i++) {
      if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
        return false;
      }
    }
    return true;
  }

  @TargetApi(16)
  private static final void maybeSetStringV16(MediaFormat format, String key,
      String value) {
    if (value != null) {
      format.setString(key, value);
    }
  }

  @TargetApi(16)
  private static final void maybeSetIntegerV16(MediaFormat format, String key,
      int value) {
    if (value != NO_VALUE) {
      format.setInteger(key, value);
    }
  }

  @TargetApi(16)
  private static final void maybeSetFloatV16(MediaFormat format, String key,
      float value) {
    if (value != NO_VALUE) {
      format.setFloat(key, value);
    }
  }

}
