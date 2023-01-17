/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.video.ColorInfo;

/** Consumer of encoded media samples, raw audio or raw video frames. */
public interface SampleConsumer {

  /**
   * Returns whether the consumer should be fed with decoded sample data. If false, encoded sample
   * data should be fed.
   *
   * <p>Can be called on any thread.
   */
  boolean expectsDecodedData();

  // Methods to pass compressed input or raw audio input.

  /**
   * Returns a buffer if the consumer is ready to accept input, and {@code null} otherwise.
   *
   * <p>If the consumer is ready to accept input and this method is called multiple times before
   * {@linkplain #queueInputBuffer() queuing} input, the same {@link DecoderInputBuffer} instance is
   * returned.
   *
   * <p>Should only be used for compressed data and raw audio data.
   */
  @Nullable
  default DecoderInputBuffer getInputBuffer() {
    throw new UnsupportedOperationException();
  }

  /**
   * Informs the consumer that its input buffer contains new input.
   *
   * <p>Should be called after filling the input buffer from {@link #getInputBuffer()} with new
   * input.
   *
   * <p>Should only be used for compressed data and raw audio data.
   */
  default void queueInputBuffer() {
    throw new UnsupportedOperationException();
  }

  // Methods to pass raw video input.

  /**
   * Returns the input {@link Surface}, where the consumer reads input frames from.
   *
   * <p>Should only be used for raw video data.
   *
   * <p>Can be called on any thread.
   */
  default Surface getInputSurface() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the expected input {@link ColorInfo}.
   *
   * <p>Should only be used for raw video data.
   *
   * <p>Can be called on any thread.
   */
  default ColorInfo getExpectedColorInfo() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the number of input video frames pending in the consumer. Pending input frames are
   * frames that have been {@linkplain #registerVideoFrame() registered} but not processed off the
   * {@linkplain #getInputSurface() input surface} yet.
   *
   * <p>Should only be used for raw video data.
   *
   * <p>Can be called on any thread.
   */
  default int getPendingVideoFrameCount() {
    throw new UnsupportedOperationException();
  }

  /**
   * Informs the consumer that a frame will be queued to the {@linkplain #getInputSurface() input
   * surface}.
   *
   * <p>Must be called before rendering a frame to the input surface.
   *
   * <p>Should only be used for raw video data.
   *
   * <p>Can be called on any thread.
   */
  default void registerVideoFrame() {
    throw new UnsupportedOperationException();
  }

  /**
   * Informs the consumer that no further input frames will be rendered.
   *
   * <p>Should only be used for raw video data.
   *
   * <p>Can be called on any thread.
   */
  default void signalEndOfVideoInput() {
    throw new UnsupportedOperationException();
  }
}
