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

import static androidx.media3.common.util.Assertions.checkState;

import androidx.annotation.CallSuper;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Manages a GLSL shader program for processing a frame. Implementations generally copy input pixels
 * into an output frame, with changes to pixels specific to the implementation.
 *
 * <p>{@code SingleFrameGlShaderProgram} implementations must produce exactly one output frame per
 * input frame with the same presentation timestamp. For more flexibility, implement {@link
 * GlShaderProgram} directly.
 *
 * <p>All methods in this class must be called on the thread that owns the OpenGL context.
 */
@UnstableApi
public abstract class SingleFrameGlShaderProgram implements GlShaderProgram {

  private final boolean useHdr;

  private GlObjectsProvider glObjectsProvider;
  private InputListener inputListener;
  private OutputListener outputListener;
  private ErrorListener errorListener;
  private Executor errorListenerExecutor;
  private int inputWidth;
  private int inputHeight;
  private @MonotonicNonNull GlTextureInfo outputTexture;
  private boolean outputTextureInUse;
  private boolean frameProcessingStarted;

  /**
   * Creates a {@code SingleFrameGlShaderProgram} instance.
   *
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   */
  public SingleFrameGlShaderProgram(boolean useHdr) {
    this.useHdr = useHdr;
    glObjectsProvider = GlObjectsProvider.DEFAULT;
    inputListener = new InputListener() {};
    outputListener = new OutputListener() {};
    errorListener = (videoFrameProcessingException) -> {};
    errorListenerExecutor = MoreExecutors.directExecutor();
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
  public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {
    checkState(
        !frameProcessingStarted,
        "The GlObjectsProvider cannot be set after frame processing has started.");
    this.glObjectsProvider = glObjectsProvider;
  }

  @Override
  public final void setInputListener(InputListener inputListener) {
    this.inputListener = inputListener;
    if (!outputTextureInUse) {
      inputListener.onReadyToAcceptInputFrame();
    }
  }

  @Override
  public final void setOutputListener(OutputListener outputListener) {
    this.outputListener = outputListener;
  }

  @Override
  public final void setErrorListener(Executor errorListenerExecutor, ErrorListener errorListener) {
    this.errorListenerExecutor = errorListenerExecutor;
    this.errorListener = errorListener;
  }

  @Override
  public final void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    checkState(
        !outputTextureInUse,
        "The shader program does not currently accept input frames. Release prior output frames"
            + " first.");
    frameProcessingStarted = true;
    try {
      if (outputTexture == null
          || inputTexture.width != inputWidth
          || inputTexture.height != inputHeight) {
        configureOutputTexture(inputTexture.width, inputTexture.height);
      }
      outputTextureInUse = true;
      GlUtil.focusFramebufferUsingCurrentContext(
          outputTexture.fboId, outputTexture.width, outputTexture.height);
      glObjectsProvider.clearOutputFrame();
      drawFrame(inputTexture.texId, presentationTimeUs);
      inputListener.onInputFrameProcessed(inputTexture);
      outputListener.onOutputFrameAvailable(outputTexture, presentationTimeUs);
    } catch (VideoFrameProcessingException | GlUtil.GlException | RuntimeException e) {
      errorListenerExecutor.execute(
          () ->
              errorListener.onError(
                  e instanceof VideoFrameProcessingException
                      ? (VideoFrameProcessingException) e
                      : new VideoFrameProcessingException(e)));
    }
  }

  @EnsuresNonNull("outputTexture")
  private void configureOutputTexture(int inputWidth, int inputHeight)
      throws GlUtil.GlException, VideoFrameProcessingException {
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    Size outputSize = configure(inputWidth, inputHeight);
    if (outputTexture == null
        || outputSize.getWidth() != outputTexture.width
        || outputSize.getHeight() != outputTexture.height) {
      if (outputTexture != null) {
        GlUtil.deleteTexture(outputTexture.texId);
        GlUtil.deleteFbo(outputTexture.fboId);
      }
      int outputTexId = GlUtil.createTexture(outputSize.getWidth(), outputSize.getHeight(), useHdr);
      outputTexture =
          glObjectsProvider.createBuffersForTexture(
              outputTexId, outputSize.getWidth(), outputSize.getHeight());
    }
  }

  @Override
  public final void releaseOutputFrame(GlTextureInfo outputTexture) {
    outputTextureInUse = false;
    frameProcessingStarted = true;
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  public final void signalEndOfCurrentInputStream() {
    frameProcessingStarted = true;
    outputListener.onCurrentOutputStreamEnded();
  }

  @Override
  @CallSuper
  public void flush() {
    outputTextureInUse = false;
    frameProcessingStarted = true;
    inputListener.onFlush();
    inputListener.onReadyToAcceptInputFrame();
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    frameProcessingStarted = true;
    if (outputTexture != null) {
      try {
        GlUtil.deleteTexture(outputTexture.texId);
        GlUtil.deleteFbo(outputTexture.fboId);
      } catch (GlUtil.GlException e) {
        throw new VideoFrameProcessingException(e);
      }
    }
  }
}
