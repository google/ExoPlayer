/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.mediacodec;

import android.media.MediaCodec;
import android.media.MediaFormat;
import com.google.android.exoplayer2.decoder.CryptoInfo;

/**
 * Abstracts {@link MediaCodec} operations.
 *
 * <p>{@code MediaCodecAdapter} offers a common interface to interact with a {@link MediaCodec}
 * regardless of the {@link
 * com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.MediaCodecOperationMode} the {@link
 * MediaCodec} is operating in.
 *
 * @see com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.MediaCodecOperationMode
 */
/* package */ interface MediaCodecAdapter {

  /**
   * Starts this instance.
   *
   * @see MediaCodec#start().
   */
  void start();

  /**
   * Returns the next available input buffer index from the underlying {@link MediaCodec} or {@link
   * MediaCodec#INFO_TRY_AGAIN_LATER} if no such buffer exists.
   *
   * @throws IllegalStateException If the underlying {@link MediaCodec} raised an error.
   */
  int dequeueInputBufferIndex();

  /**
   * Returns the next available output buffer index from the underlying {@link MediaCodec}. If the
   * next available output is a MediaFormat change, it will return {@link
   * MediaCodec#INFO_OUTPUT_FORMAT_CHANGED} and you should call {@link #getOutputFormat()} to get
   * the format. If there is no available output, this method will return {@link
   * MediaCodec#INFO_TRY_AGAIN_LATER}.
   *
   * @throws IllegalStateException If the underlying {@link MediaCodec} raised an error.
   */
  int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo);

  /**
   * Gets the {@link MediaFormat} that was output from the {@link MediaCodec}.
   *
   * <p>Call this method if a previous call to {@link #dequeueOutputBufferIndex} returned {@link
   * MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
   */
  MediaFormat getOutputFormat();

  /**
   * Submit an input buffer for decoding.
   *
   * <p>The {@code index} must be an input buffer index that has been obtained from a previous call
   * to {@link #dequeueInputBufferIndex()}.
   *
   * @see MediaCodec#queueInputBuffer
   */
  void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);

  /**
   * Submit an input buffer that is potentially encrypted for decoding.
   *
   * <p>The {@code index} must be an input buffer index that has been obtained from a previous call
   * to {@link #dequeueInputBufferIndex()}.
   *
   * <p>Note: This method behaves as {@link MediaCodec#queueSecureInputBuffer} with the difference
   * that {@code info} is of type {@link CryptoInfo} and not {@link
   * android.media.MediaCodec.CryptoInfo}.
   *
   * @see MediaCodec#queueSecureInputBuffer
   */
  void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags);

  /**
   * Flushes the {@code MediaCodecAdapter}.
   *
   * <p>Note: {@link #flush()} should also call any {@link MediaCodec} methods needed to flush the
   * {@link MediaCodec}, i.e., {@link MediaCodec#flush()} and <em>optionally</em> {@link
   * MediaCodec#start()}, if the {@link MediaCodec} operates in asynchronous mode.
   */
  void flush();

  /**
   * Shutdown the {@code MediaCodecAdapter}.
   *
   * <p>Note: This method does not release the underlying {@link MediaCodec}. Make sure to call
   * {@link #shutdown()} before stopping or releasing the underlying {@link MediaCodec} to ensure
   * the adapter is fully shutdown before the {@link MediaCodec} stops executing. Otherwise, there
   * is a risk the adapter might interact with a stopped or released {@link MediaCodec}.
   */
  void shutdown();
}
