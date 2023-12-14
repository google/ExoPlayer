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
import androidx.media3.common.C;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.OnInputFrameProcessedListener;
import androidx.media3.common.util.GlUtil;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Forwards frames made available via {@linkplain GLES10#GL_TEXTURE_2D traditional GLES textures} to
 * a {@link GlShaderProgram} for consumption.
 */
/* package */ final class TexIdTextureManager extends TextureManager {
  private @MonotonicNonNull FrameConsumptionManager frameConsumptionManager;

  private @MonotonicNonNull OnInputFrameProcessedListener frameProcessedListener;
  private @MonotonicNonNull FrameInfo inputFrameInfo;
  private final GlObjectsProvider glObjectsProvider;

  /**
   * Creates a new instance.
   *
   * @param glObjectsProvider The {@link GlObjectsProvider} for using EGL and GLES.
   * @param videoFrameProcessingTaskExecutor The {@link VideoFrameProcessingTaskExecutor}.
   */
  public TexIdTextureManager(
      GlObjectsProvider glObjectsProvider,
      VideoFrameProcessingTaskExecutor videoFrameProcessingTaskExecutor) {
    super(videoFrameProcessingTaskExecutor);
    this.glObjectsProvider = glObjectsProvider;
  }

  @Override
  public void onReadyToAcceptInputFrame() {
    checkNotNull(frameConsumptionManager);
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
  public void setSamplingGlShaderProgram(GlShaderProgram samplingGlShaderProgram) {
    frameConsumptionManager =
        new FrameConsumptionManager(
            glObjectsProvider, samplingGlShaderProgram, videoFrameProcessingTaskExecutor);
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
          checkNotNull(frameConsumptionManager).queueInputFrame(inputTexture, presentationTimeUs);
          DebugTraceUtil.logEvent(
              DebugTraceUtil.EVENT_VFP_QUEUE_TEXTURE,
              presentationTimeUs,
              /* extraFormat= */ "%dx%d",
              /* extraArgs...= */ frameInfo.width,
              frameInfo.height);
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
    return checkNotNull(frameConsumptionManager).getPendingFrameCount();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    videoFrameProcessingTaskExecutor.submit(
        () -> {
          checkNotNull(frameConsumptionManager).signalEndOfCurrentStream();
          DebugTraceUtil.logEvent(
              DebugTraceUtil.EVENT_TEX_ID_TEXTURE_MANAGER_SIGNAL_EOS, C.TIME_END_OF_SOURCE);
        });
  }

  @Override
  public void release() {
    // Do nothing.
  }

  // Methods that must be called on the GL thread.

  @Override
  protected synchronized void flush() {
    checkNotNull(frameConsumptionManager).onFlush();
    super.flush();
  }
}
