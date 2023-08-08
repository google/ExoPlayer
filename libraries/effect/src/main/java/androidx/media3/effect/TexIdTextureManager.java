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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.opengl.GLES10;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Forwards frames made available via {@linkplain GLES10#GL_TEXTURE_2D traditional GLES textures} to
 * a {@link GlShaderProgram} for consumption.
 *
 * <p>Public methods in this class can be called from any thread.
 */
/* package */ final class TexIdTextureManager implements TextureManager {
  private final VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor;
  private final FrameConsumptionManager frameConsumptionManager;

  private @MonotonicNonNull OnInputFrameProcessedListener frameProcessedListener;
  private @MonotonicNonNull FrameInfo inputFrameInfo;

  /**
   * Creates a new instance.
   *
   * @param glObjectsProvider The {@link GlObjectsProvider} for using EGL and GLES.
   * @param shaderProgram The {@link GlShaderProgram} for which this {@code texIdTextureManager}
   *     will be set as the {@link GlShaderProgram.InputListener}.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   */
  public TexIdTextureManager(
      GlObjectsProvider glObjectsProvider,
      GlShaderProgram shaderProgram,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    this.videoFrameProcessingTaskExecutor = videoFrameProcessingTaskExecutor;
    frameConsumptionManager =
        new FrameConsumptionManager(
            glObjectsProvider, shaderProgram, videoFrameProcessingTaskExecutor);
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    videoFrameProcessingTaskExecutor.submit(frameConsumptionManager::onReadyToAcceptInputFrame);
  }

  @Override
  public void onInputFrameProcessed(GlTextureInfo inputTexture) {
    videoFrameProcessingTaskExecutor.submit(
        () ->
            checkNotNull(frameProcessedListener)
                .onInputFrameProcessed(inputTexture.texId, GlUtil.createGlSyncFence()));
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
          DebugTraceUtil.logEvent(
              DebugTraceUtil.EVENT_VFP_QUEUE_TEXTURE,
              presentationTimeUs,
              /* extra= */ frameInfo.width + "x" + frameInfo.height);
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
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          frameConsumptionManager.signalEndOfCurrentStream();
          DebugTraceUtil.logEvent(
              DebugTraceUtil.EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
        });
  }

  @Override
  public void setOnFlushCompleteListener(@Nullable VideoFrameProcessingTaskExecutor.Task task) {
    // Do nothing.
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    // Do nothing.
  }
}
