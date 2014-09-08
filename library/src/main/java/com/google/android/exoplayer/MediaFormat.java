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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Defines the format of an elementary media stream.
 */
public class MediaFormat {

  public static final int NO_VALUE = -1;

  public final String mimeType;
  public final int maxInputSize;

  public final int width;
  public final int height;

  public final int channelCount;
  public final int sampleRate;

  private int maxWidth;
  private int maxHeight;

  public final List<byte[]> initializationData;

  // Lazy-initialized hashcode.
  private int hashCode;
  // Possibly-lazy-initialized framework media format.
  private android.media.MediaFormat frameworkMediaFormat;

  @TargetApi(16)
  public static MediaFormat createFromFrameworkMediaFormatV16(android.media.MediaFormat format) {
    return new MediaFormat(format);
  }

  public static MediaFormat createVideoFormat(String mimeType, int maxInputSize, int width,
      int height, List<byte[]> initializationData) {
    return new MediaFormat(mimeType, maxInputSize, width, height, NO_VALUE, NO_VALUE,
        initializationData);
  }

  public static MediaFormat createAudioFormat(String mimeType, int maxInputSize, int channelCount,
      int sampleRate, List<byte[]> initializationData) {
    return new MediaFormat(mimeType, maxInputSize, NO_VALUE, NO_VALUE, channelCount, sampleRate,
        initializationData);
  }

  @TargetApi(16)
  private MediaFormat(android.media.MediaFormat format) {
    this.frameworkMediaFormat = format;
    mimeType = format.getString(android.media.MediaFormat.KEY_MIME);
    maxInputSize = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE);
    width = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_WIDTH);
    height = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT);
    channelCount = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT);
    sampleRate = getOptionalIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE);
    initializationData = new ArrayList<byte[]>();
    for (int i = 0; format.containsKey("csd-" + i); i++) {
      ByteBuffer buffer = format.getByteBuffer("csd-" + i);
      byte[] data = new byte[buffer.limit()];
      buffer.get(data);
      initializationData.add(data);
      buffer.flip();
    }
    maxWidth = NO_VALUE;
    maxHeight = NO_VALUE;
  }

  private MediaFormat(String mimeType, int maxInputSize, int width, int height, int channelCount,
      int sampleRate, List<byte[]> initializationData) {
    this.mimeType = mimeType;
    this.maxInputSize = maxInputSize;
    this.width = width;
    this.height = height;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.initializationData = initializationData == null ? Collections.<byte[]>emptyList()
        : initializationData;
    maxWidth = NO_VALUE;
    maxHeight = NO_VALUE;
  }

  public void setMaxVideoDimensions(int maxWidth, int maxHeight) {
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    if (frameworkMediaFormat != null) {
      maybeSetMaxDimensionsV16(frameworkMediaFormat);
    }
  }

  public int getMaxVideoWidth() {
    return maxWidth;
  }

  public int getMaxVideoHeight() {
    return maxHeight;
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + mimeType == null ? 0 : mimeType.hashCode();
      result = 31 * result + maxInputSize;
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + maxWidth;
      result = 31 * result + maxHeight;
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      for (int i = 0; i < initializationData.size(); i++) {
        result = 31 * result + Arrays.hashCode(initializationData.get(i));
      }
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
    return equalsInternal((MediaFormat) obj, false);
  }

  public boolean equals(MediaFormat other, boolean ignoreMaxDimensions) {
    if (this == other) {
      return true;
    }
    if (other == null) {
      return false;
    }
    return equalsInternal(other, ignoreMaxDimensions);
  }

  private boolean equalsInternal(MediaFormat other, boolean ignoreMaxDimensions) {
    if (maxInputSize != other.maxInputSize || width != other.width || height != other.height
        || (!ignoreMaxDimensions && (maxWidth != other.maxWidth || maxHeight != other.maxHeight))
        || channelCount != other.channelCount || sampleRate != other.sampleRate
        || !Util.areEqual(mimeType, other.mimeType)
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

  @Override
  public String toString() {
    return "MediaFormat(" + mimeType + ", " + maxInputSize + ", " + width + ", " + height + ", " +
        channelCount + ", " + sampleRate + ", " + maxWidth + ", " + maxHeight + ")";
  }

  /**
   * @return A {@link MediaFormat} representation of this format.
   */
  @TargetApi(16)
  public final android.media.MediaFormat getFrameworkMediaFormatV16() {
    if (frameworkMediaFormat == null) {
      android.media.MediaFormat format = new android.media.MediaFormat();
      format.setString(android.media.MediaFormat.KEY_MIME, mimeType);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_WIDTH, width);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_HEIGHT, height);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_CHANNEL_COUNT, channelCount);
      maybeSetIntegerV16(format, android.media.MediaFormat.KEY_SAMPLE_RATE, sampleRate);
      for (int i = 0; i < initializationData.size(); i++) {
        format.setByteBuffer("csd-" + i, ByteBuffer.wrap(initializationData.get(i)));
      }
      maybeSetMaxDimensionsV16(format);
      frameworkMediaFormat = format;
    }
    return frameworkMediaFormat;
  }

  @SuppressLint("InlinedApi")
  @TargetApi(16)
  private final void maybeSetMaxDimensionsV16(android.media.MediaFormat format) {
    maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_WIDTH, maxWidth);
    maybeSetIntegerV16(format, android.media.MediaFormat.KEY_MAX_HEIGHT, maxHeight);
  }

  @TargetApi(16)
  private static final void maybeSetIntegerV16(android.media.MediaFormat format, String key,
      int value) {
    if (value != NO_VALUE) {
      format.setInteger(key, value);
    }
  }

  @TargetApi(16)
  private static final int getOptionalIntegerV16(android.media.MediaFormat format,
      String key) {
    return format.containsKey(key) ? format.getInteger(key) : NO_VALUE;
  }

}
