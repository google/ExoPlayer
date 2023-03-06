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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.effect.GlShaderProgram.InputListener;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forwards externally produced frames that become available via a {@link SurfaceTexture} to an
 * {@link ExternalShaderProgram} for consumption.
 */
/* package */ final class ExternalTextureManager implements InputListener {

  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final ExternalShaderProgram externalShaderProgram;
  private final int externalTexId;
  private final Surface surface;
  private final SurfaceTexture surfaceTexture;
  private final float[] textureTransformMatrix;
  private final Queue<FrameInfo> pendingFrames;

  // Incremented on any thread, decremented on the GL thread only.
  private final AtomicInteger externalShaderProgramInputCapacity;
  // Counts the frames that are registered before flush but are made available after flush.
  // Read and written only on GL thread.
  private int numberOfFramesToDropOnBecomingAvailable;

  // Read and written only on GL thread.
  private int availableFrameCount;

  // Set to true on any thread. Read on the GL thread only.
  private volatile boolean inputStreamEnded;
  // The frame that is sent downstream and is not done processing yet.
  // Set to null on any thread. Read and set to non-null on the GL thread only.
  @Nullable private volatile FrameInfo currentFrame;

  // TODO(b/238302341) Remove the use of after flush task, block the calling thread instead.
  @Nullable private volatile VideoFrameProcessingTask onFlushCompleteTask;

  private long previousStreamOffsetUs;

  /**
   * Creates a new instance.
   *
   * @param externalShaderProgram The {@link ExternalShaderProgram} for which this {@code
   *     ExternalTextureManager} will be set as the {@link InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   * @throws VideoFrameProcessingException If a problem occurs while creating the external texture.
   */
  // The onFrameAvailableListener will not be invoked until the constructor returns.
  @SuppressWarnings("nullness:method.invocation.invalid")
  public ExternalTextureManager(
      ExternalShaderProgram externalShaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor)
      throws VideoFrameProcessingException {
    this.externalShaderProgram = externalShaderProgram;
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    try {
      externalTexId = GlUtil.createExternalTexture();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
    surfaceTexture = new SurfaceTexture(externalTexId);
    textureTransformMatrix = new float[16];
    pendingFrames = new ConcurrentLinkedQueue<>();
    externalShaderProgramInputCapacity = new AtomicInteger();
    previousStreamOffsetUs = C.TIME_UNSET;
    surfaceTexture.setOnFrameAvailableListener(
        unused ->
            videoFrameProcessingTaskExecutor.submit(
                () -> {
                  if (numberOfFramesToDropOnBecomingAvailable > 0) {
                    numberOfFramesToDropOnBecomingAvailable--;
                    surfaceTexture.updateTexImage();
                    maybeExecuteAfterFlushTask();
                  } else {
                    availableFrameCount++;
                    maybeQueueFrameToExternalShaderProgram();
                  }
                }));
    surface = new Surface(surfaceTexture);
  }

  /** See {@link DefaultVideoFrameProcessor#setInputDefaultBufferSize}. */
  public void setDefaultBufferSize(int width, int height) {
    surfaceTexture.setDefaultBufferSize(width, height);
  }

  /** Returns the {@linkplain Surface input surface} that wraps the external texture. */
  public Surface getInputSurface() {
    return surface;
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          externalShaderProgramInputCapacity.incrementAndGet();
          maybeQueueFrameToExternalShaderProgram();
        });
  }

  @Override
  public void onInputFrameProcessed(GlTextureInfo inputTexture) {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          currentFrame = null;
          maybeQueueFrameToExternalShaderProgram();
        });
  }

  /** Sets the task to run on completing flushing, or {@code null} to clear any task. */
  public void setOnFlushCompleteListener(@Nullable VideoFrameProcessingTask task) {
    onFlushCompleteTask = task;
  }

  @Override
  public void onFlush() {
    videoFrameProcessingTaskExecutor.submit(this::flush);
  }

  /**
   * Notifies the {@code ExternalTextureManager} that a frame with the given {@link FrameInfo} will
   * become available via the {@link SurfaceTexture} eventually.
   *
   * <p>Can be called on any thread. The caller must ensure that frames are registered in the
   * correct order.
   */
  public void registerInputFrame(FrameInfo frame) {
    pendingFrames.add(frame);
  }

  /**
   * Returns the number of {@linkplain #registerInputFrame(FrameInfo) registered} frames that have
   * not been sent to the downstream {@link ExternalShaderProgram} yet.
   *
   * <p>Can be called on any thread.
   */
  public int getPendingFrameCount() {
    return pendingFrames.size();
  }

  /**
   * Signals the end of the input.
   *
   * @see VideoFrameProcessor#signalEndOfInput()
   */
  public void signalEndOfInput() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          inputStreamEnded = true;
          if (pendingFrames.isEmpty() && currentFrame == null) {
            externalShaderProgram.signalEndOfCurrentInputStream();
          }
        });
  }

  public void release() {
    surfaceTexture.release();
    surface.release();
  }

  private void maybeExecuteAfterFlushTask() {
    if (onFlushCompleteTask == null || numberOfFramesToDropOnBecomingAvailable > 0) {
      return;
    }
    videoFrameProcessingTaskExecutor.submitWithHighPriority(onFlushCompleteTask);
  }

  // Methods that must be called on the GL thread.

  private void flush() {
    // A frame that is registered before flush may arrive after flush.
    numberOfFramesToDropOnBecomingAvailable = pendingFrames.size() - availableFrameCount;
    while (availableFrameCount > 0) {
      availableFrameCount--;
      surfaceTexture.updateTexImage();
    }
    externalShaderProgramInputCapacity.set(0);
    currentFrame = null;
    pendingFrames.clear();
    maybeExecuteAfterFlushTask();
  }

  private void maybeQueueFrameToExternalShaderProgram() {
    if (externalShaderProgramInputCapacity.get() == 0
        || availableFrameCount == 0
        || currentFrame != null) {
      return;
    }

    surfaceTexture.updateTexImage();
    availableFrameCount--;
    this.currentFrame = pendingFrames.peek();

    FrameInfo currentFrame = checkStateNotNull(this.currentFrame);
    externalShaderProgramInputCapacity.decrementAndGet();
    surfaceTexture.getTransformMatrix(textureTransformMatrix);
    externalShaderProgram.setTextureTransformMatrix(textureTransformMatrix);
    long frameTimeNs = surfaceTexture.getTimestamp();
    long offsetToAddUs = currentFrame.offsetToAddUs;
    long streamOffsetUs = currentFrame.streamOffsetUs;
    if (streamOffsetUs != previousStreamOffsetUs) {
      if (previousStreamOffsetUs != C.TIME_UNSET) {
        externalShaderProgram.signalEndOfCurrentInputStream();
      }
      previousStreamOffsetUs = streamOffsetUs;
    }
    // Correct the presentation time so that GlShaderPrograms don't see the stream offset.
    long presentationTimeUs = (frameTimeNs / 1000) + offsetToAddUs - streamOffsetUs;
    externalShaderProgram.queueInputFrame(
        new GlTextureInfo(
            externalTexId,
            /* fboId= */ C.INDEX_UNSET,
            /* rboId= */ C.INDEX_UNSET,
            currentFrame.width,
            currentFrame.height),
        presentationTimeUs);
    checkStateNotNull(pendingFrames.remove());

    if (inputStreamEnded && pendingFrames.isEmpty()) {
      externalShaderProgram.signalEndOfCurrentInputStream();
    }
  }
}
