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

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;

/**
 * Defines constants that are generally useful throughout the library.
 */
public final class C {

  /**
   * Represents an unknown microsecond time or duration.
   */
  public static final long UNKNOWN_TIME_US = -1L;

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
   * @see MediaExtractor#SAMPLE_FLAG_SYNC
   */
  @SuppressWarnings("InlinedApi")
  public static final int SAMPLE_FLAG_SYNC = MediaExtractor.SAMPLE_FLAG_SYNC;

  /**
   * @see MediaExtractor#SAMPLE_FLAG_ENCRYPTED
   */
  @SuppressWarnings("InlinedApi")
  public static final int SAMPLE_FLAG_ENCRYPTED = MediaExtractor.SAMPLE_FLAG_ENCRYPTED;

  /**
   * Indicates that a sample should be decoded but not rendered.
   */
  public static final int SAMPLE_FLAG_DECODE_ONLY = 0x8000000;

  /**
   * A return value for methods where the end of an input was encountered.
   */
  public static final int RESULT_END_OF_INPUT = -1;

  /**
   * A prefix for custom ExoPlayer WebVTT headers.
   *
   * @hide
   */
  public static final String WEBVTT_EXO_HEADER = "EXO-HEADER";

  /**
   * An element of a custom ExoPlayer WebVTT header. An {@code WEBVTT_OFFSET + value} element can
   * be added to a custom ExoPlayer WebVTT header to specify an offset time (in microseconds) that
   * should be subtracted from the embedded MPEGTS value.
   *
   * @hide
   */
  public static final String WEBVTT_EXO_HEADER_OFFSET = "OFFSET:";

  private C() {}

}
