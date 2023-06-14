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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.effect.GlShaderProgram.InputListener;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forwards externally produced frames that become available via a {@link SurfaceTexture} to an
 * {@link ExternalShaderProgram} for consumption.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class ExternalTextureManager implements TextureManager {

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

  // Read and written on the GL thread only.
  private boolean inputStreamEnded;

  // Read and written on the GL thread only.
  private boolean currentInputStreamEnded;

  // The frame that is sent downstream and is not done processing yet.
  // Set to null on any thread. Read and set to non-null on the GL thread only.
  @Nullable private volatile FrameInfo currentFrame;

  // TODO(b/238302341) Remove the use of after flush task, block the calling thread instead.
  @Nullable private volatile VideoFrameProcessingTask onFlushCompleteTask;

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
    surfaceTexture.setOnFrameAvailableListener(
        unused ->
            videoFrameProcessingTaskExecutor.submit(
                () -> {
                  DebugTraceUtil.recordFrameRenderedToVideoFrameProcessorInput();
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

  @Override
  public void setDefaultBufferSize(int width, int height) {
    surfaceTexture.setDefaultBufferSize(width, height);
  }

  @Override
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
          if (currentInputStreamEnded && pendingFrames.isEmpty()) {
            // Reset because there could be further input streams after the current one ends.
            currentInputStreamEnded = false;
            externalShaderProgram.signalEndOfCurrentInputStream();
            DebugTraceUtil.recordExternalInputManagerSignalEndOfCurrentInputStream();
          } else {
            maybeQueueFrameToExternalShaderProgram();
          }
        });
  }

  @Override
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
  @Override
  public void registerInputFrame(FrameInfo frame) {
    checkState(!inputStreamEnded);
    pendingFrames.add(frame);
  }

  /**
   * Returns the number of {@linkplain #registerInputFrame(FrameInfo) registered} frames that have
   * not been sent to the downstream {@link ExternalShaderProgram} yet.
   *
   * <p>Can be called on any thread.
   */
  @Override
  public int getPendingFrameCount() {
    return pendingFrames.size();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          if (pendingFrames.isEmpty() && currentFrame == null) {
            externalShaderProgram.signalEndOfCurrentInputStream();
            DebugTraceUtil.recordExternalInputManagerSignalEndOfCurrentInputStream();
          } else {
            currentInputStreamEnded = true;
          }
        });
  }

  @Override
  public void signalEndOfInput() {
    // TODO(b/274109008) Consider remove inputStreamEnded boolean.
    videoFrameProcessingTaskExecutor.submit(() -> inputStreamEnded = true);
  }

  @Override
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
    // Correct the presentation time so that GlShaderPrograms don't see the stream offset.
    long presentationTimeUs = (frameTimeNs / 1000) + offsetToAddUs;
    externalShaderProgram.queueInputFrame(
        new GlTextureInfo(
            externalTexId,
            /* fboId= */ C.INDEX_UNSET,
            /* rboId= */ C.INDEX_UNSET,
            currentFrame.width,
            currentFrame.height),
        presentationTimeUs);
    checkStateNotNull(pendingFrames.remove());
    DebugTraceUtil.recordFrameDequeuedFromVideoFrameProcessorInput();
    // If the queued frame is the last frame, end of stream will be signaled onInputFrameProcessed.
  }
}
