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
 *
 */

package com.google.android.exoplayer2.effect;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessor;

/** A component that handles {@code DefaultVideoFrameProcessor}'s input. */
/* package */ interface InputHandler extends GlShaderProgram.InputListener {

  /**
   * See {@link DefaultVideoFrameProcessor#setInputDefaultBufferSize}.
   *
   * <p>Only works when the input is received on a {@link SurfaceTexture}.
   */
  default void setDefaultBufferSize(int width, int height) {
    throw new UnsupportedOperationException();
  }

  /**
   * Provides an input {@link Bitmap} to put into the video frames.
   *
   * @param inputBitmap The {@link Bitmap} queued to the {@code VideoFrameProcessor}.
   * @param durationUs The duration for which to display the {@code inputBitmap}, in microseconds.
   * @param offsetUs The offset, from the start of the input stream, to apply for the {@code
   *     inputBitmap} in microseconds.
   * @param frameRate The frame rate at which to display the {@code inputBitmap}, in frames per
   *     second.
   * @param useHdr Whether input and/or output colors are HDR.
   */
  default void queueInputBitmap(
      Bitmap inputBitmap, long durationUs, long offsetUs, float frameRate, boolean useHdr) {
    throw new UnsupportedOperationException();
  }

  /**
   * See {@link VideoFrameProcessor#getInputSurface}.
   *
   * <p>Only works when the input is received on a {@link SurfaceTexture}.
   */
  default Surface getInputSurface() {
    throw new UnsupportedOperationException();
  }

  /** Informs the {@code InputHandler} that a frame will be queued. */
  default void registerInputFrame(FrameInfo frameInfo) {
    throw new UnsupportedOperationException();
  }

  /** See {@link VideoFrameProcessor#getPendingInputFrameCount}. */
  int getPendingFrameCount();

  /**
   * Signals the end of the input.
   *
   * @see VideoFrameProcessor#signalEndOfInput()
   */
  void signalEndOfInput();

  /** Sets the task to run on completing flushing, or {@code null} to clear any task. */
  void setOnFlushCompleteListener(@Nullable VideoFrameProcessingTask task);

  /**
   * Releases all resources.
   *
   * @see VideoFrameProcessor#release()
   */
  void release();
}
