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
import android.view.Surface;

import java.util.UUID;

/**
 * Defines constants that are generally useful throughout the library.
 */
public final class C {

  /**
   * Special microsecond constant representing the end of a source.
   */
  public static final long END_OF_SOURCE_US = Long.MIN_VALUE;

  /**
   * Special microsecond constant representing an unset or unknown time or duration.
   */
  public static final long UNSET_TIME_US = Long.MIN_VALUE + 1;

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

  /**
   * A type constant for metadata tracks.
   */
  public static final int TRACK_TYPE_METADATA = 4;

  /**
  * A default size in bytes for an individual allocation that forms part of a larger buffer.
  */
  public static final int DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024;

  /**
   * A default size in bytes for a video buffer.
   */
  public static final int DEFAULT_VIDEO_BUFFER_SIZE = 200 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for an audio buffer.
   */
  public static final int DEFAULT_AUDIO_BUFFER_SIZE = 54 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a text buffer.
   */
  public static final int DEFAULT_TEXT_BUFFER_SIZE = 2 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a muxed buffer (e.g. containing video, audio and text).
   */
  public static final int DEFAULT_MUXED_BUFFER_SIZE = DEFAULT_VIDEO_BUFFER_SIZE
      + DEFAULT_AUDIO_BUFFER_SIZE + DEFAULT_TEXT_BUFFER_SIZE;

  /**
   * The Nil UUID as defined by
   * <a href="https://tools.ietf.org/html/rfc4122#section-4.1.7">RFC4122</a>.
   */
  public static final UUID UUID_NIL = new UUID(0L, 0L);

  /**
   * The type of a message that can be passed to an video {@link TrackRenderer} via
   * {@link ExoPlayer#sendMessage} or {@link ExoPlayer#blockingSendMessage}. The message object
   * should be the target {@link Surface}, or null.
   */
  public static final int MSG_SET_SURFACE = 1;

  private C() {}

}
