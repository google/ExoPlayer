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

import androidx.annotation.CallSuper;
import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executor;

/**
 * A base implementation of {@link GlShaderProgram}.
 *
 * <p>{@code BaseGlShaderProgram} manages an output texture pool, whose size is configurable on
 * construction. An implementation should manage a GLSL shader program for processing frames.
 * Override {@link #drawFrame} to customize drawing. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>{@code BaseShaderProgram} implementations can produce any number of output frames per input
 * frame with the same presentation timestamp.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
@UnstableApi
public abstract class BaseGlShaderProgram implements GlShaderProgram {
  protected final TexturePool outputTexturePool;
  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private int inputWidth;
  private int inputHeight;

  /**
   * Creates a {@code BaseGlShaderProgram} instance.
   *
   * @param useHighPrecisionColorComponents If {@code false}, uses colors with 8-bit unsigned bytes.
   *     If {@code true}, use 16-bit (half-precision) floating-point.
   * @param texturePoolCapacity The capacity of the texture pool. For example, if implementing a
   *     texture cache, the size should be the number of textures to cache.
   */
  public BaseGlShaderProgram(boolean useHighPrecisionColorComponents, int texturePoolCapacity) {
    outputTexturePool = new TexturePool(useHighPrecisionColorComponents, texturePoolCapacity);
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (frameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
    inputWidth = C.LENGTH_UNSET;
    inputHeight = C.LENGTH_UNSET;
  }

  /**
   * Configures the instance based on the input dimensions.
   *
   * <p>This method must be called before {@linkplain #drawFrame(int,long) drawing} the first frame
   * and before drawing subsequent frames with different input dimensions.
   *
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   * @return The output width and height of frames processed through {@link #drawFrame(int, long)}.
   * @throws VideoFrameProcessingException If an error occurs while configuring.
   */
  public abstract Size configure(int inputWidth, int inputHeight)
      throws VideoFrameProcessingException;

  /**
   * Draws one frame.
   *
   * <p>This method may only be called after the shader program has been {@link #configure(int, int)
   * configured}. The caller is responsible for focussing the correct render target before calling
   * this method.
   *
   * <p>A minimal implementation should tell OpenGL to use its shader program, bind the shader
   * program's vertex attributes and uniforms, and issue a drawing command.
   *
   * @param inputTexId Identifier of a 2D OpenGL texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws VideoFrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException;

  @Override
  public void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    for (int i = 0; i < outputTexturePool.freeTextureCount(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  /**
   * Returns {@code true} if the texture buffer should be cleared before calling {@link #drawFrame}
   * or {@code false} if it should retain the content of the last drawn frame.
   *
   * <p>When returning {@code false}, the shader program must clear the texture before first drawing
   * to it, because textures are not zero-initialized when created. This can be done by calling
   * {@link GlUtil#clearFocusedBuffers()}.
   */
  public boolean shouldClearTextureBuffer() {
    // TODO - b/309428083: Clear the texture before first use.
    return true;
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    try {
      if (inputWidth != inputTexture.width
          || inputHeight != inputTexture.height
          || !outputTexturePool.isConfigured()) {
        inputWidth = inputTexture.width;
        inputHeight = inputTexture.height;
        Size outputTextureSize = configure(inputTexture.width, inputTexture.height);
        outputTexturePool.ensureConfigured(
            glObjectsProvider, outputTextureSize.getWidth(), outputTextureSize.getHeight());
      }

      // Focus on the next free buffer.
      GlTextureInfo outputTexture = outputTexturePool.useTexture();

      // Copy frame to fbo.
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      if (shouldClearTextureBuffer()) {
        GlUtil.clearFocusedBuffers();
      }
      drawFrame(inputTexture.texId, presentationTimeUs);
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (VideoFrameProcessingException | GlUtil.GlException e) {
      errorListenerExecutor.execute(
          () -> errorListener.onError(VideoFrameProcessingException.from(e)));
    }
  }

  @Override
  public void releaseOutputFrame(GlTextureInfo outputTexture) {
    if (!outputTexturePool.isUsingTexture(outputTexture)) {
      // This allows us to ignore outputTexture instances not associated with this
      // BaseGlShaderProgram instance. This may happen if a BaseGlShaderProgram is introduced into
      // the GlShaderProgram chain after frames already exist in the pipeline.
      // TODO - b/320481157: Consider removing this if condition and disallowing disconnecting a
      //  GlShaderProgram while it still has in-use frames.
      return;
    }
    outputTexturePool.freeTexture(outputTexture);
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  @CallSuper
  public void flush() {
    outputTexturePool.freeAllTextures();
    inputListener.onFlush();
    for (int i = 0; i < outputTexturePool.capacity(); i++) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    try {
      outputTexturePool.deleteAllTextures();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  protected final InputListener getInputListener() {
    return inputListener;
  }

  protected final OutputListener getOutputListener() {
    return outputListener;
  }

  protected final void onError(Exception e) {
    errorListenerExecutor.execute(
        () -> errorListener.onError(VideoFrameProcessingException.from(e)));
  }
}
