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
package com.google.android.exoplayer2.transformerdemo;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.util.Size;
import com.google.android.exoplayer2.transformer.FrameProcessingException;
import com.google.android.exoplayer2.transformer.SingleFrameGlTextureProcessor;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.LibraryLoader;
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
/* package */ final class MediaPipeProcessor implements SingleFrameGlTextureProcessor {

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

  private final String graphName;
  private final String inputStreamName;
  private final String outputStreamName;
  private final ConditionVariable frameProcessorConditionVariable;

  private @MonotonicNonNull FrameProcessor frameProcessor;
  private int inputWidth;
  private int inputHeight;
  private int inputTexId;
  private @MonotonicNonNull GlProgram glProgram;
  private @MonotonicNonNull TextureFrame outputFrame;
  private @MonotonicNonNull RuntimeException frameProcessorPendingError;

  /**
   * Creates a new texture processor that wraps a MediaPipe graph.
   *
   * @param graphName Name of a MediaPipe graph asset to load.
   * @param inputStreamName Name of the input video stream in the graph.
   * @param outputStreamName Name of the input video stream in the graph.
   */
  public MediaPipeProcessor(String graphName, String inputStreamName, String outputStreamName) {
    checkState(LOADER.isAvailable());
    this.graphName = graphName;
    this.inputStreamName = inputStreamName;
    this.outputStreamName = outputStreamName;
    frameProcessorConditionVariable = new ConditionVariable();
  }

  @Override
  public void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException {
    this.inputTexId = inputTexId;
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    glProgram = new GlProgram(context, COPY_VERTEX_SHADER_NAME, COPY_FRAGMENT_SHADER_NAME);

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
  }

  @Override
  public Size getOutputSize() {
    return new Size(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(long presentationTimeUs) throws FrameProcessingException {
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
    checkStateNotNull(frameProcessor).close();
  }
}
