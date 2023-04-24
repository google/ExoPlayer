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

package androidx.media3.effect;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.VideoFrameProcessor;

/** A component that handles {@code DefaultVideoFrameProcessor}'s input. */
/* package */ interface InputHandler extends GlShaderProgram.InputListener {

  /**
   * See {@link DefaultVideoFrameProcessor#setInputDefaultBufferSize}.
   *
   * <p>Only works when the input is received on a {@link SurfaceTexture}.
   */
  void setDefaultBufferSize(int width, int height);

  /**
   * Provides an input {@link Bitmap} to put into the video frames.
   *
   * @see VideoFrameProcessor#queueInputBitmap
   */
  void queueInputBitmap(Bitmap inputBitmap, long durationUs, float frameRate, boolean useHdr);

  /**
   * See {@link VideoFrameProcessor#getInputSurface}.
   *
   * <p>Only works when the input is received on a {@link SurfaceTexture}.
   */
  Surface getInputSurface();

  /** Informs the {@code InputHandler} that a frame will be queued. */
  void registerInputFrame(FrameInfo frameInfo);

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
