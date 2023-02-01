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
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.C;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.FrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.effect.GlTextureProcessor.InputListener;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forwards externally produced frames that become available via a {@link SurfaceTexture} to an
 * {@link ExternalTextureProcessor} for consumption.
 */
/* package */ class ExternalTextureManager implements InputListener {

  private final FrameProcessingTaskExecutor frameProcessingTaskExecutor;
  private final ExternalTextureProcessor externalTextureProcessor;
  private final int externalTexId;
  private final SurfaceTexture surfaceTexture;
  private final float[] textureTransformMatrix;
  private final Queue<FrameInfo> pendingFrames;

  // Incremented on any thread, decremented on the GL thread only.
  private final AtomicInteger externalTextureProcessorInputCapacity;
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

  @Nullable private volatile FrameProcessingTask onFlushCompleteTask;

  private long previousStreamOffsetUs;

  /**
   * Creates a new instance.
   *
   * @param externalTextureProcessor The {@link ExternalTextureProcessor} for which this {@code
   *     ExternalTextureManager} will be set as the {@link InputListener}.
   * @param frameProcessingTaskExecutor The {@link FrameProcessingTaskExecutor}.
   * @throws FrameProcessingException If a problem occurs while creating the external texture.
   */
  public ExternalTextureManager(
      ExternalTextureProcessor externalTextureProcessor,
      FrameProcessingTaskExecutor frameProcessingTaskExecutor)
      throws FrameProcessingException {
    this.externalTextureProcessor = externalTextureProcessor;
    this.frameProcessingTaskExecutor = frameProcessingTaskExecutor;
    try {
      externalTexId = GlUtil.createExternalTexture();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }
    surfaceTexture = new SurfaceTexture(externalTexId);
    textureTransformMatrix = new float[16];
    pendingFrames = new ConcurrentLinkedQueue<>();
    externalTextureProcessorInputCapacity = new AtomicInteger();

    previousStreamOffsetUs = C.TIME_UNSET;
  }

  public SurfaceTexture getSurfaceTexture() {
    surfaceTexture.setOnFrameAvailableListener(
        unused ->
            frameProcessingTaskExecutor.submit(
                () -> {
                  if (numberOfFramesToDropOnBecomingAvailable > 0) {
                    numberOfFramesToDropOnBecomingAvailable--;
                    surfaceTexture.updateTexImage();
                  } else {
                    availableFrameCount++;
                    maybeQueueFrameToExternalTextureProcessor();
                  }
                }));
    return surfaceTexture;
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    frameProcessingTaskExecutor.submit(
        () -> {
          externalTextureProcessorInputCapacity.incrementAndGet();
          maybeQueueFrameToExternalTextureProcessor();
        });
  }

  @Override
  public void onInputFrameProcessed(TextureInfo inputTexture) {
    frameProcessingTaskExecutor.submit(
        () -> {
          currentFrame = null;
          maybeQueueFrameToExternalTextureProcessor();
        });
  }

  /** Sets the task to run on completing flushing, or {@code null} to clear any task. */
  public void setOnFlushCompleteListener(@Nullable FrameProcessingTask task) {
    onFlushCompleteTask = task;
  }

  @Override
  public void onFlush() {
    frameProcessingTaskExecutor.submit(this::flush);
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
   * not been sent to the downstream {@link ExternalTextureProcessor} yet.
   *
   * <p>Can be called on any thread.
   */
  public int getPendingFrameCount() {
    return pendingFrames.size();
  }

  /**
   * Signals the end of the input.
   *
   * @see FrameProcessor#signalEndOfInput()
   */
  public void signalEndOfInput() {
    frameProcessingTaskExecutor.submit(
        () -> {
          inputStreamEnded = true;
          if (pendingFrames.isEmpty() && currentFrame == null) {
            externalTextureProcessor.signalEndOfCurrentInputStream();
          }
        });
  }

  public void release() {
    surfaceTexture.release();
  }

  @WorkerThread
  private void flush() {
    // A frame that is registered before flush may arrive after flush.
    numberOfFramesToDropOnBecomingAvailable = pendingFrames.size() - availableFrameCount;
    while (availableFrameCount > 0) {
      availableFrameCount--;
      surfaceTexture.updateTexImage();
    }
    externalTextureProcessorInputCapacity.set(0);
    currentFrame = null;
    pendingFrames.clear();

    if (onFlushCompleteTask != null) {
      frameProcessingTaskExecutor.submitWithHighPriority(onFlushCompleteTask);
    }
  }

  @WorkerThread
  private void maybeQueueFrameToExternalTextureProcessor() {
    if (externalTextureProcessorInputCapacity.get() == 0
        || availableFrameCount == 0
        || currentFrame != null) {
      return;
    }

    surfaceTexture.updateTexImage();
    availableFrameCount--;
    this.currentFrame = pendingFrames.peek();

    FrameInfo currentFrame = checkStateNotNull(this.currentFrame);
    externalTextureProcessorInputCapacity.decrementAndGet();
    surfaceTexture.getTransformMatrix(textureTransformMatrix);
    externalTextureProcessor.setTextureTransformMatrix(textureTransformMatrix);
    long frameTimeNs = surfaceTexture.getTimestamp();
    long offsetToAddUs = currentFrame.offsetToAddUs;
    long streamOffsetUs = currentFrame.streamOffsetUs;
    if (streamOffsetUs != previousStreamOffsetUs) {
      if (previousStreamOffsetUs != C.TIME_UNSET) {
        externalTextureProcessor.signalEndOfCurrentInputStream();
      }
      previousStreamOffsetUs = streamOffsetUs;
    }
    // Correct the presentation time so that processors don't see the stream offset.
    long presentationTimeUs = (frameTimeNs / 1000) + offsetToAddUs - streamOffsetUs;
    externalTextureProcessor.queueInputFrame(
        new TextureInfo(
            externalTexId, /* fboId= */ C.INDEX_UNSET, currentFrame.width, currentFrame.height),
        presentationTimeUs);
    checkStateNotNull(pendingFrames.remove());

    if (inputStreamEnded && pendingFrames.isEmpty()) {
      externalTextureProcessor.signalEndOfCurrentInputStream();
    }
  }
}
