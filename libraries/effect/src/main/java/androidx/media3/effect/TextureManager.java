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
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.TimestampIterator;

/**
 * Handles {@code DefaultVideoFrameProcessor}'s input.
 *
 * <p>All instance methods must be called from either the thread that owns {@code this} instance, or
 * an internal GL thread.
 */
/* package */ abstract class TextureManager implements GlShaderProgram.InputListener {

  protected final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;

  private final Object lock;

  // TODO(b/238302341) Remove the use of onFlushCompleteTask, block the calling thread instead.
  @GuardedBy("lock")
  @Nullable
  private VideoFrameProcessingTaskExecutor.Task onFlushCompleteTask;

  /**
   * Creates a new instance.
   *
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   */
  public TextureManager(VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    lock = new Object();
  }

  /**
   * See {@link DefaultVideoFrameProcessor#setInputDefaultBufferSize}.
   *
   * <p>Only works when the input is received on a {@link SurfaceTexture}.
   */
  public void setDefaultBufferSize(int width, int height) {
    throw new UnsupportedOperationException();
  }

  /**
   * Sets the {@link GlShaderProgram} that consumes the {@link TextureManager}'s output.
   *
   * <p>Must be called before any method that queues input or {@link
   * #signalEndOfCurrentInputStream()}.
   */
  public abstract void setSamplingGlShaderProgram(GlShaderProgram samplingGlShaderProgram);

  /**
   * Provides an input {@link Bitmap} to put into the video frames.
   *
   * @param inputBitmap The {@link Bitmap} queued to the {@code VideoFrameProcessor}.
   * @param frameInfo Information about the bitmap being queued.
   * @param inStreamOffsetsUs The times within the current stream that the bitmap should be shown
   *     at. The timestamps should be monotonically increasing.
   * @param useHdr Whether input and/or output colors are HDR.
   */
  public void queueInputBitmap(
      Bitmap inputBitmap,
      FrameInfo frameInfo,
      TimestampIterator inStreamOffsetsUs,
      boolean useHdr) {
    throw new UnsupportedOperationException();
  }

  /**
   * Provides an input texture ID to the {@code VideoFrameProcessor}.
   *
   * @see VideoFrameProcessor#queueInputTexture
   */
  public void queueInputTexture(int inputTexId, long presentationTimeUs) {
    throw new UnsupportedOperationException();
  }

  /**
   * Sets the {@link OnInputFrameProcessedListener}.
   *
   * @see VideoFrameProcessor#setOnInputFrameProcessedListener
   */
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    throw new UnsupportedOperationException();
  }

  /**
   * Sets information about the input frames.
   *
   * <p>The new input information is applied from the next frame {@linkplain #registerInputFrame
   * registered} or {@linkplain #queueInputTexture queued} onwards.
   *
   * <p>Pixels are expanded using the {@link FrameInfo#pixelWidthHeightRatio} so that the output
   * frames' pixels have a ratio of 1.
   */
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    // Do nothing.
  }

  /**
   * See {@link VideoFrameProcessor#getInputSurface}.
   *
   * <p>Only works when the input is received on a {@link SurfaceTexture}.
   */
  public Surface getInputSurface() {
    throw new UnsupportedOperationException();
  }

  /** Informs the {@code TextureManager} that a frame will be queued. */
  public void registerInputFrame(FrameInfo frameInfo) {
    throw new UnsupportedOperationException();
  }

  /** See {@link VideoFrameProcessor#getPendingInputFrameCount}. */
  public abstract int getPendingFrameCount();

  /** Signals the end of the current input stream. */
  public abstract void signalEndOfCurrentInputStream();

  /** Sets the task to run on completing flushing, or {@code null} to clear any task. */
  public final void setOnFlushCompleteListener(
      @Nullable VideoFrameProcessingTaskExecutor.Task task) {
    synchronized (lock) {
      onFlushCompleteTask = task;
    }
  }

  @Override
  public final void onFlush() {
    videoFrameProcessingTaskExecutor.submit(this::flush);
  }

  /**
   * Releases all resources.
   *
   * @see VideoFrameProcessor#release()
   */
  public abstract void release() throws VideoFrameProcessingException;

  /** Clears any pending data. Must be called on the GL thread. */
  protected void flush() {
    synchronized (lock) {
      if (onFlushCompleteTask != null) {
        videoFrameProcessingTaskExecutor.submitWithHighPriority(onFlushCompleteTask);
      }
    }
  }
}
