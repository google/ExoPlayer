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

import android.media.AudioFormat;
import android.media.MediaCodec;

/**
 * Defines constants that are generally useful throughout the library.
 */
public final class C {

  /**
   * Special microsecond constant representing an unknown time or duration.
   */
  public static final long UNKNOWN_TIME_US = -1L;

  /**
   * Special microsecond constant representing the end of a source.
   */
  public static final long END_OF_SOURCE_US = -2L;

  /**
   * The number of microseconds in one second.
   */
  public static final long MICROS_PER_SECOND = 1000000L;

  /**
   * Represents an unbounded length of data.
   */
  public static final int LENGTH_UNBOUNDED = -1;

  /**
   * The name of the UTF-8 charset.
   */
  public static final String UTF8_NAME = "UTF-8";

  /**
   * @see MediaCodec#CRYPTO_MODE_AES_CTR
   */
  @SuppressWarnings("InlinedApi")
  public static final int CRYPTO_MODE_AES_CTR = MediaCodec.CRYPTO_MODE_AES_CTR;

  /**
   * @see AudioFormat#ENCODING_AC3
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_AC3 = AudioFormat.ENCODING_AC3;

  /**
   * @see AudioFormat#ENCODING_E_AC3
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_E_AC3 = AudioFormat.ENCODING_E_AC3;

  /**
   * @see AudioFormat#ENCODING_DTS
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_DTS = AudioFormat.ENCODING_DTS;

  /**
   * @see AudioFormat#ENCODING_DTS_HD
   */
  @SuppressWarnings("InlinedApi")
  public static final int ENCODING_DTS_HD = AudioFormat.ENCODING_DTS_HD;

  /**
   * @see AudioFormat#CHANNEL_OUT_7POINT1_SURROUND
   */
  @SuppressWarnings({"InlinedApi", "deprecation"})
  public static final int CHANNEL_OUT_7POINT1_SURROUND = Util.SDK_INT < 23
      ? AudioFormat.CHANNEL_OUT_7POINT1 : AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;

  /**
   * Indicates that a buffer holds a synchronization sample.
   */
  @SuppressWarnings("InlinedApi")
  public static final int BUFFER_FLAG_KEY_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME;

  /**
   * Flag for empty buffers that signal that the end of the stream was reached.
   */
  @SuppressWarnings("InlinedApi")
  public static final int BUFFER_FLAG_END_OF_STREAM = MediaCodec.BUFFER_FLAG_END_OF_STREAM;

  /**
   * Indicates that a buffer is (at least partially) encrypted.
   */
  public static final int BUFFER_FLAG_ENCRYPTED = 0x40000000;

  /**
   * Indicates that a buffer should be decoded but not rendered.
   */
  public static final int BUFFER_FLAG_DECODE_ONLY = 0x80000000;

  /**
   * A return value for methods where the end of an input was encountered.
   */
  public static final int RESULT_END_OF_INPUT = -1;

  /**
   * A return value for methods where the length of parsed data exceeds the maximum length allowed.
   */
  public static final int RESULT_MAX_LENGTH_EXCEEDED = -2;

  /**
   * A type constant for tracks of unknown type.
   */
  public static final int TRACK_TYPE_UNKNOWN = -1;

  /**
   * A type constant for tracks of some default type, where the type itself is unknown.
   */
  public static final int TRACK_TYPE_DEFAULT = 0;

  /**
   * A type constant for audio tracks.
   */
  public static final int TRACK_TYPE_AUDIO = 1;

  /**
   * A type constant for video tracks.
   */
  public static final int TRACK_TYPE_VIDEO = 2;

  /**
   * A type constant for text tracks.
   */
  public static final int TRACK_TYPE_TEXT = 3;

  private C() {}

}
