/*
 * Copyright 2023 The Android Open Source Project
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
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.decoder.CryptoInfo;

/**
 * Interface to queue buffers to a {@link MediaCodec}.
 *
 * <p>All methods must be called from the same thread.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ interface MediaCodecBufferEnqueuer {

  /**
   * Starts this instance.
   *
   * <p>Call this method after creating an instance and before queueing input buffers.
   */
  void start();

  /**
   * Submits an input buffer for decoding.
   *
   * @see android.media.MediaCodec#queueInputBuffer
   */
  void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags);

  /**
   * Submits an input buffer that potentially contains encrypted data for decoding.
   *
   * <p>Note: This method behaves as {@link MediaCodec#queueSecureInputBuffer} with the difference
   * that {@code info} is of type {@link CryptoInfo} and not {@link MediaCodec.CryptoInfo}.
   *
   * @see MediaCodec#queueSecureInputBuffer
   */
  void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags);

  /**
   * Submits new codec parameters that should be applied from the next queued input buffer.
   *
   * @see MediaCodec#setParameters(Bundle)
   */
  @RequiresApi(19)
  void setParameters(Bundle parameters);

  /** Flushes the instance. */
  void flush();

  /** Shuts down the instance. Make sure to call this method to release its internal resources. */
  void shutdown();

  /** Blocks the current thread until all input buffers pending queueing are submitted. */
  void waitUntilQueueingComplete() throws InterruptedException;

  /** Throw any exception that occurred during the enqueueing process. */
  void maybeThrowException();
}
