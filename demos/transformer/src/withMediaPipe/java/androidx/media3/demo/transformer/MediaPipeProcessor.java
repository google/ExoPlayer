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
package androidx.media3.demo.transformer;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.util.Size;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.LibraryLoader;
import androidx.media3.transformer.FrameProcessingException;
import androidx.media3.transformer.SingleFrameGlTextureProcessor;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AppTextureFrame;
import com.google.mediapipe.framework.TextureFrame;
import com.google.mediapipe.glutil.EglManager;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Runs a MediaPipe graph on input frames. The implementation is currently limited to graphs that
 * can immediately produce one output frame per input frame.
 */
/* package */ final class MediaPipeProcessor extends SingleFrameGlTextureProcessor {

  private static final LibraryLoader LOADER =
      new LibraryLoader("mediapipe_jni") {
        @Override
        protected void loadLibrary(String name) {
          System.loadLibrary(name);
        }
      };

  static {
    // Not all build configurations require OpenCV to be loaded separately, so attempt to load the
    // library but ignore the error if it's not present.
    try {
      System.loadLibrary("opencv_java3");
    } catch (UnsatisfiedLinkError e) {
      // Do nothing.
    }
  }

  private static final String COPY_VERTEX_SHADER_NAME = "vertex_shader_copy_es2.glsl";
  private static final String COPY_FRAGMENT_SHADER_NAME = "shaders/fragment_shader_copy_es2.glsl";

  private final ConditionVariable frameProcessorConditionVariable;
  private final FrameProcessor frameProcessor;
  private final GlProgram glProgram;

  private int inputWidth;
  private int inputHeight;
  private @MonotonicNonNull TextureFrame outputFrame;
  private @MonotonicNonNull RuntimeException frameProcessorPendingError;

  /**
   * Creates a new texture processor that wraps a MediaPipe graph.
   *
   * @param context The {@link Context}.
   * @param graphName Name of a MediaPipe graph asset to load.
   * @param inputStreamName Name of the input video stream in the graph.
   * @param outputStreamName Name of the input video stream in the graph.
   * @throws IOException If a problem occurs while reading shader files or initializing MediaPipe
   *     resources.
   */
  public MediaPipeProcessor(
      Context context, String graphName, String inputStreamName, String outputStreamName)
      throws IOException {
    checkState(LOADER.isAvailable());

    frameProcessorConditionVariable = new ConditionVariable();
    AndroidAssetUtil.initializeNativeAssetManager(context);
    EglManager eglManager = new EglManager(EGL14.eglGetCurrentContext());
    frameProcessor =
        new FrameProcessor(
            context, eglManager.getNativeContext(), graphName, inputStreamName, outputStreamName);
    // Unblock drawFrame when there is an output frame or an error.
    frameProcessor.setConsumer(
        frame -> {
          outputFrame = frame;
          frameProcessorConditionVariable.open();
        });
    frameProcessor.setAsynchronousErrorListener(
        error -> {
          frameProcessorPendingError = error;
          frameProcessorConditionVariable.open();
        });
    glProgram = new GlProgram(context, COPY_VERTEX_SHADER_NAME, COPY_FRAGMENT_SHADER_NAME);
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    return new Size(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    frameProcessorConditionVariable.close();

    // Pass the input frame to MediaPipe.
    AppTextureFrame appTextureFrame = new AppTextureFrame(inputTexId, inputWidth, inputHeight);
    appTextureFrame.setTimestamp(presentationTimeUs);
    checkStateNotNull(frameProcessor).onNewFrame(appTextureFrame);

    // Wait for output to be passed to the consumer.
    try {
      frameProcessorConditionVariable.block();
    } catch (InterruptedException e) {
      // Propagate the interrupted flag so the next blocking operation will throw.
      // TODO(b/230469581): The next processor that runs will not have valid input due to returning
      //  early here. This could be fixed by checking for interruption in the outer loop that runs
      //  through the texture processors.
      Thread.currentThread().interrupt();
      return;
    }

    if (frameProcessorPendingError != null) {
      throw new FrameProcessingException(frameProcessorPendingError);
    }

    // Copy from MediaPipe's output texture to the current output.
    try {
      checkStateNotNull(glProgram).use();
      glProgram.setSamplerTexIdUniform(
          "uTexSampler", checkStateNotNull(outputFrame).getTextureName(), /* texUnitIndex= */ 0);
      glProgram.setBufferAttribute(
          "aFramePosition",
          GlUtil.getNormalizedCoordinateBounds(),
          GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
      glProgram.bindAttributesAndUniforms();
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    } finally {
      checkStateNotNull(outputFrame).release();
    }
  }

  @Override
  public void release() {
    super.release();
    checkStateNotNull(frameProcessor).close();
  }
}
