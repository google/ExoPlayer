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
public interface C {

  /**
   * Special microsecond constant representing the end of a source.
   */
  long END_OF_SOURCE_US = Long.MIN_VALUE;

  /**
   * Special microsecond constant representing an unset or unknown time or duration.
   */
  long UNSET_TIME_US = Long.MIN_VALUE + 1;

  /**
   * The number of microseconds in one second.
   */
  long MICROS_PER_SECOND = 1000000L;

  /**
   * The number of nanoseconds in one second.
   */
  long NANOS_PER_SECOND = 1000000000L;

  /**
   * Represents an unbounded length of data.
   */
  int LENGTH_UNBOUNDED = -1;

  /**
   * The name of the UTF-8 charset.
   */
  String UTF8_NAME = "UTF-8";

  /**
   * @see MediaCodec#CRYPTO_MODE_AES_CTR
   */
  @SuppressWarnings("InlinedApi")
  int CRYPTO_MODE_AES_CTR = MediaCodec.CRYPTO_MODE_AES_CTR;

  /**
   * @see AudioFormat#ENCODING_INVALID
   */
  int ENCODING_INVALID = AudioFormat.ENCODING_INVALID;

  /**
   * @see AudioFormat#ENCODING_PCM_8BIT
   */
  int ENCODING_PCM_8BIT = AudioFormat.ENCODING_PCM_8BIT;

  /**
   * @see AudioFormat#ENCODING_PCM_16BIT
   */
  int ENCODING_PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT;

  /**
   * PCM encoding with 24 bits per sample.
   */
  int ENCODING_PCM_24BIT = 0x80000000;

  /**
   * PCM encoding with 32 bits per sample.
   */
  int ENCODING_PCM_32BIT = 0x40000000;

  /**
   * @see AudioFormat#ENCODING_AC3
   */
  @SuppressWarnings("InlinedApi")
  int ENCODING_AC3 = AudioFormat.ENCODING_AC3;

  /**
   * @see AudioFormat#ENCODING_E_AC3
   */
  @SuppressWarnings("InlinedApi")
  int ENCODING_E_AC3 = AudioFormat.ENCODING_E_AC3;

  /**
   * @see AudioFormat#ENCODING_DTS
   */
  @SuppressWarnings("InlinedApi")
  int ENCODING_DTS = AudioFormat.ENCODING_DTS;

  /**
   * @see AudioFormat#ENCODING_DTS_HD
   */
  @SuppressWarnings("InlinedApi")
  int ENCODING_DTS_HD = AudioFormat.ENCODING_DTS_HD;

  /**
   * @see AudioFormat#CHANNEL_OUT_7POINT1_SURROUND
   */
  @SuppressWarnings({"InlinedApi", "deprecation"})
  int CHANNEL_OUT_7POINT1_SURROUND = Util.SDK_INT < 23
      ? AudioFormat.CHANNEL_OUT_7POINT1 : AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;

  /**
   * Indicates that a buffer holds a synchronization sample.
   */
  @SuppressWarnings("InlinedApi")
  int BUFFER_FLAG_KEY_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME;

  /**
   * Flag for empty buffers that signal that the end of the stream was reached.
   */
  @SuppressWarnings("InlinedApi")
  int BUFFER_FLAG_END_OF_STREAM = MediaCodec.BUFFER_FLAG_END_OF_STREAM;

  /**
   * Indicates that a buffer is (at least partially) encrypted.
   */
  int BUFFER_FLAG_ENCRYPTED = 0x40000000;

  /**
   * Indicates that a buffer should be decoded but not rendered.
   */
  int BUFFER_FLAG_DECODE_ONLY = 0x80000000;

  /**
   * A return value for methods where the end of an input was encountered.
   */
  int RESULT_END_OF_INPUT = -1;

  /**
   * A return value for methods where the length of parsed data exceeds the maximum length allowed.
   */
  int RESULT_MAX_LENGTH_EXCEEDED = -2;

  /**
   * A data type constant for data of unknown or unspecified type.
   */
  int DATA_TYPE_UNKNOWN = 0;

  /**
   * A data type constant for media, typically containing media samples.
   */
  int DATA_TYPE_MEDIA = 1;

  /**
   * A data type constant for media, typically containing only initialization data.
   */
  int DATA_TYPE_MEDIA_INITIALIZATION = 2;

  /**
   * A data type constant for drm or encryption related data.
   */
  int DATA_TYPE_DRM = 3;

  /**
   * A data type constant for a manifest file.
   */
  int DATA_TYPE_MANIFEST = 4;

  /**
   * Applications or extensions may define custom {@code DATA_TYPE_*} constants greater than or
   * equal to this value.
   */
  int DATA_TYPE_CUSTOM_BASE = 10000;

  /**
   * A type constant for tracks of unknown type.
   */
  int TRACK_TYPE_UNKNOWN = -1;

  /**
   * A type constant for tracks of some default type, where the type itself is unknown.
   */
  int TRACK_TYPE_DEFAULT = 0;

  /**
   * A type constant for audio tracks.
   */
  int TRACK_TYPE_AUDIO = 1;

  /**
   * A type constant for video tracks.
   */
  int TRACK_TYPE_VIDEO = 2;

  /**
   * A type constant for text tracks.
   */
  int TRACK_TYPE_TEXT = 3;

  /**
   * A type constant for metadata tracks.
   */
  int TRACK_TYPE_METADATA = 4;

  /**
  * A default size in bytes for an individual allocation that forms part of a larger buffer.
  */
  int DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024;

  /**
   * A default size in bytes for a video buffer.
   */
  int DEFAULT_VIDEO_BUFFER_SIZE = 200 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for an audio buffer.
   */
  int DEFAULT_AUDIO_BUFFER_SIZE = 54 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a text buffer.
   */
  int DEFAULT_TEXT_BUFFER_SIZE = 2 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a metadata buffer.
   */
  int DEFAULT_METADATA_BUFFER_SIZE = 2 * DEFAULT_BUFFER_SEGMENT_SIZE;

  /**
   * A default size in bytes for a muxed buffer (e.g. containing video, audio and text).
   */
  int DEFAULT_MUXED_BUFFER_SIZE = DEFAULT_VIDEO_BUFFER_SIZE
      + DEFAULT_AUDIO_BUFFER_SIZE + DEFAULT_TEXT_BUFFER_SIZE;

  /**
   * The Nil UUID as defined by
   * <a href="https://tools.ietf.org/html/rfc4122#section-4.1.7">RFC4122</a>.
   */
  UUID UUID_NIL = new UUID(0L, 0L);

  /**
   * UUID for the Widevine DRM scheme.
   * <p></p>
   * Widevine is supported on Android devices running Android 4.3 (API Level 18) and up.
   */
  UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

  /**
   * UUID for the PlayReady DRM scheme.
   * <p>
   * PlayReady is supported on all AndroidTV devices. Note that most other Android devices do not
   * provide PlayReady support.
   */
  UUID PLAYREADY_UUID = new UUID(0x9A04F07998404286L, 0xAB92E65BE0885F95L);

  /**
   * The type of a message that can be passed to a video {@link TrackRenderer} via
   * {@link ExoPlayer#sendMessages} or {@link ExoPlayer#blockingSendMessages}. The message object
   * should be the target {@link Surface}, or null.
   */
  int MSG_SET_SURFACE = 1;

  /**
   * The type of a message that can be passed to an audio {@link TrackRenderer} via
   * {@link ExoPlayer#sendMessages} or {@link ExoPlayer#blockingSendMessages}. The message object
   * should be a {@link Float} with 0 being silence and 1 being unity gain.
   */
  int MSG_SET_VOLUME = 2;

  /**
   * The type of a message that can be passed to an audio {@link TrackRenderer} via
   * {@link ExoPlayer#sendMessages} or {@link ExoPlayer#blockingSendMessages}. The message object
   * should be a {@link android.media.PlaybackParams}, which will be used to configure the
   * underlying {@link android.media.AudioTrack}. The message object should not be modified by the
   * caller after it has been passed
   */
  int MSG_SET_PLAYBACK_PARAMS = 3;

  /**
   * Applications or extensions may define custom {@code MSG_*} constants greater than or equal to
   * this value.
   */
  int MSG_CUSTOM_BASE = 10000;

}
