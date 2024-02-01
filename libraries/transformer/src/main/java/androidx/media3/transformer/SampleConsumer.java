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
package androidx.media3.transformer;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.graphics.Bitmap;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.util.TimestampIterator;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Consumer of encoded media samples, raw audio or raw video frames. */
@UnstableApi
public interface SampleConsumer {

  /**
   * Specifies the result of an input operation. One of {@link #INPUT_RESULT_SUCCESS}, {@link
   * #INPUT_RESULT_TRY_AGAIN_LATER} or {@link #INPUT_RESULT_END_OF_STREAM}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({INPUT_RESULT_SUCCESS, INPUT_RESULT_TRY_AGAIN_LATER, INPUT_RESULT_END_OF_STREAM})
  @interface InputResult {}

  /**
   * The operation of queueing input was successful.
   *
   * <p>The caller can queue more input or signal {@link #signalEndOfVideoInput() signal end of
   * input}.
   */
  int INPUT_RESULT_SUCCESS = 1;

  /**
   * The operation of queueing/registering input was unsuccessful.
   *
   * <p>The caller should queue try again later.
   */
  int INPUT_RESULT_TRY_AGAIN_LATER = 2;

  /**
   * The operation of queueing input successful and end of input has been automatically signalled.
   *
   * <p>The caller should not {@link #signalEndOfVideoInput() signal end of input} as this has
   * already been done internally.
   */
  int INPUT_RESULT_END_OF_STREAM = 3;

  /**
   * Returns a {@link DecoderInputBuffer}, if available.
   *
   * <p>This {@linkplain DecoderInputBuffer buffer} should be filled with new input data and
   * {@linkplain #queueInputBuffer() queued} to the consumer.
   *
   * <p>If this method returns a non-null buffer:
   *
   * <ul>
   *   <li>The buffer's {@linkplain DecoderInputBuffer#data data} is non-null.
   *   <li>The same buffer instance is returned if this method is called multiple times before
   *       {@linkplain #queueInputBuffer() queuing} input.
   * </ul>
   *
   * <p>Should only be used for compressed data and raw audio data.
   */
  @Nullable
  default DecoderInputBuffer getInputBuffer() {
    throw new UnsupportedOperationException();
  }

  /**
   * Attempts to queue new input to the consumer.
   *
   * <p>The input buffer from {@link #getInputBuffer()} should be filled with the new input before
   * calling this method.
   *
   * <p>An input buffer should not be used anymore after it has been successfully queued.
   *
   * <p>Should only be used for compressed data and raw audio data.
   *
   * @return Whether the input was successfully queued. If {@code false}, the caller should try
   *     again later.
   */
  default boolean queueInputBuffer() {
    throw new UnsupportedOperationException();
  }

  /**
   * Attempts to provide an input {@link Bitmap} to the consumer.
   *
   * <p>Should only be used for image data.
   *
   * @param inputBitmap The {@link Bitmap} to queue to the consumer.
   * @param timestampIterator A {@link TimestampIterator} generating the exact timestamps that the
   *     bitmap should be shown at.
   * @return The {@link InputResult} describing the result of the operation.
   */
  default @InputResult int queueInputBitmap(
      Bitmap inputBitmap, TimestampIterator timestampIterator) {
    throw new UnsupportedOperationException();
  }

  // Methods to pass raw video input.

  /**
   * Provides a {@link OnInputFrameProcessedListener} to the consumer.
   *
   * <p>Should only be used for raw video data when input is provided by texture ID.
   *
   * @param listener The {@link OnInputFrameProcessedListener}.
   */
  default void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    throw new UnsupportedOperationException();
  }

  /**
   * Attempts to provide an input texture to the consumer.
   *
   * <p>Should only be used for raw video data.
   *
   * @param texId The ID of the texture to queue to the consumer.
   * @param presentationTimeUs The presentation time for the texture, in microseconds.
   * @return The {@link InputResult} describing the result of the operation.
   */
  default @InputResult int queueInputTexture(int texId, long presentationTimeUs) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the input {@link Surface}, where the consumer reads input frames from.
   *
   * <p>Should only be used for raw video data.
   */
  default Surface getInputSurface() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the number of input video frames pending in the consumer. Pending input frames are
   * frames that have been {@linkplain #registerVideoFrame(long) registered} but not processed off
   * the {@linkplain #getInputSurface() input surface} yet.
   *
   * <p>Should only be used for raw video data.
   */
  default int getPendingVideoFrameCount() {
    throw new UnsupportedOperationException();
  }

  /**
   * Attempts to register a video frame to the consumer.
   *
   * <p>Each frame to consume should be registered using this method. After a frame is successfully
   * registered, it should be rendered to the {@linkplain #getInputSurface() input surface}.
   *
   * <p>Should only be used for raw video data.
   *
   * @param presentationTimeUs The presentation time of the frame to register, in microseconds.
   * @return Whether the frame was successfully registered. If {@code false}, the caller should try
   *     again later.
   */
  default boolean registerVideoFrame(long presentationTimeUs) {
    throw new UnsupportedOperationException();
  }

  /**
   * Informs the consumer that no further input frames will be rendered.
   *
   * <p>Should only be used for raw video data.
   */
  default void signalEndOfVideoInput() {
    throw new UnsupportedOperationException();
  }
}
