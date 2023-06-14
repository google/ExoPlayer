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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.opengl.GLES10;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.OnInputFrameProcessedListener;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Forwards a video frames made available via {@linkplain GLES10#GL_TEXTURE_2D traditional GLES
 * texture} to a {@link GlShaderProgram} for consumption.
 *
 * <p>Public methods in this class can be called from any thread.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class TexIdTextureManager implements TextureManager {
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final FrameConsumptionManager frameConsumptionManager;

  private @MonotonicNonNull OnInputFrameProcessedListener frameProcessedListener;
  private @MonotonicNonNull FrameInfo inputFrameInfo;

  /**
   * Creates a new instance.
   *
   * @param shaderProgram The {@link GlShaderProgram} for which this {@code texIdTextureManager}
   *     will be set as the {@link GlShaderProgram.InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   */
  public TexIdTextureManager(
      GlShaderProgram shaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    frameConsumptionManager =
        new FrameConsumptionManager(shaderProgram, videoFrameProcessingTaskExecutor);
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    videoFrameProcessingTaskExecutor.submit(frameConsumptionManager::onReadyToAcceptInputFrame);
  }

  @Override
  public void onInputFrameProcessed(GlTextureInfo inputTexture) {
    videoFrameProcessingTaskExecutor.submit(
        () -> checkNotNull(frameProcessedListener).onInputFrameProcessed(inputTexture.getTexId()));
  }

  @Override
  public void onFlush() {
    videoFrameProcessingTaskExecutor.submit(frameConsumptionManager::onFlush);
  }

  @Override
  public void queueInputTexture(int inputTexId, long presentationTimeUs) {
    FrameInfo frameInfo = checkNotNull(this.inputFrameInfo);
    checkNotNull(frameProcessedListener);
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          GlTextureInfo inputTexture =
              new GlTextureInfo(
                  inputTexId,
                  /* fboId= */ C.INDEX_UNSET,
                  /* rboId= */ C.INDEX_UNSET,
                  frameInfo.width,
                  frameInfo.height);
          frameConsumptionManager.queueInputFrame(inputTexture, presentationTimeUs);
        });
  }

  @Override
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    frameProcessedListener = listener;
  }

  @Override
  public void setInputFrameInfo(FrameInfo inputFrameInfo) {
    this.inputFrameInfo = inputFrameInfo;
  }

  @Override
  public int getPendingFrameCount() {
    return frameConsumptionManager.getPendingFrameCount();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    videoFrameProcessingTaskExecutor.submit(frameConsumptionManager::signalEndOfCurrentStream);
  }

  @Override
  public void signalEndOfInput() {
    // Do nothing.
  }

  @Override
  public void setOnFlushCompleteListener(@Nullable VideoFrameProcessingTask task) {
    // Do nothing.
  }

  @Override
  public void release() {
    // Do nothing.
  }
}
