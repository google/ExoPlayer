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
package androidx.media3.transformer;

import android.util.Size;
import androidx.annotation.CallSuper;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Manages a GLSL shader program for processing a frame. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>{@code SingleFrameGlTextureProcessor} implementations must produce exactly one output frame
 * per input frame with the same presentation timestamp. For more flexibility, implement {@link
 * GlTextureProcessor} directly.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
@UnstableApi
public abstract class SingleFrameGlTextureProcessor implements GlTextureProcessor {

  private @MonotonicNonNull Listener listener;
  private int inputWidth;
  private int inputHeight;
  private @MonotonicNonNull TextureInfo outputTexture;
  private boolean outputTextureInUse;

  /**
   * Configures the texture processor based on the input dimensions.
   *
   * <p>This method must be called before {@linkplain #drawFrame(int,long) drawing} the first frame
   * and before drawing subsequent frames with different input dimensions.
   *
   * @param inputWidth The input width, in pixels.
   * @param inputHeight The input height, in pixels.
   * @return The output {@link Size} of frames processed through {@link #drawFrame(int, long)}.
   */
  public abstract Size configure(int inputWidth, int inputHeight);

  /**
   * Draws one frame.
   *
   * <p>This method may only be called after the texture processor has been {@link #configure(int,
   * int) configured}. The caller is responsible for focussing the correct render target before
   * calling this method.
   *
   * <p>A minimal implementation should tell OpenGL to use its shader program, bind the shader
   * program's vertex attributes and uniforms, and issue a drawing command.
   *
   * @param inputTexId Identifier of a 2D OpenGL texture containing the input frame.
   * @param presentationTimeUs The presentation timestamp of the current frame, in microseconds.
   * @throws FrameProcessingException If an error occurs while processing or drawing the frame.
   */
  public abstract void drawFrame(int inputTexId, long presentationTimeUs)
      throws FrameProcessingException;

  @Override
  public final void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public final boolean maybeQueueInputFrame(TextureInfo inputTexture, long presentationTimeUs) {
    if (outputTextureInUse) {
      return false;
    }

    try {
      if (outputTexture == null
          || inputTexture.width != inputWidth
          || inputTexture.height != inputHeight) {
        configureOutputTexture(inputTexture.width, inputTexture.height);
      }
      outputTextureInUse = true;
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      GlUtil.clearOutputFrame();
      drawFrame(inputTexture.texId, presentationTimeUs);
      if (listener != null) {
        listener.onInputFrameProcessed(inputTexture);
        listener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
      }
    } catch (FrameProcessingException | GlUtil.GlException | RuntimeException e) {
      if (listener != null) {
        listener.onFrameProcessingError(
            e instanceof FrameProcessingException
                ? (FrameProcessingException) e
                : new FrameProcessingException(e));
      }
    }
    return true;
  }

  @EnsuresNonNull("outputTexture")
  private void configureOutputTexture(int inputWidth, int inputHeight) throws GlUtil.GlException {
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    Size outputSize = configure(inputWidth, inputHeight);
    if (outputTexture == null
        || outputSize.getWidth() != outputTexture.width
        || outputSize.getHeight() != outputTexture.height) {
      if (outputTexture != null) {
        GlUtil.deleteTexture(outputTexture.texId);
      }
      int outputTexId = GlUtil.createTexture(outputSize.getWidth(), outputSize.getHeight());
      int outputFboId = GlUtil.createFboForTexture(outputTexId);
      outputTexture =
          new TextureInfo(outputTexId, outputFboId, outputSize.getWidth(), outputSize.getHeight());
    }
  }

  @Override
  public final void releaseOutputFrame(TextureInfo outputTexture) {
    outputTextureInUse = false;
  }

  @Override
  public final void signalEndOfInputStream() {
    if (listener != null) {
      listener.onOutputStreamEnded();
    }
  }

  @Override
  @CallSuper
  public void release() throws FrameProcessingException {
    if (outputTexture != null) {
      try {
        GlUtil.deleteTexture(outputTexture.texId);
      } catch (GlUtil.GlException e) {
        throw new FrameProcessingException(e);
      }
    }
  }
}
