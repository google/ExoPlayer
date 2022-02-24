/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;
import android.view.SurfaceView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** FrameEditor applies changes to individual video frames. */
/* package */ final class FrameEditor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /**
   * Returns a new {@code FrameEditor} for applying changes to individual frames.
   *
   * @param context A {@link Context}.
   * @param outputWidth The output width in pixels.
   * @param outputHeight The output height in pixels.
   * @param pixelWidthHeightRatio The ratio of width over height, for each pixel.
   * @param transformationMatrix The transformation matrix to apply to each frame.
   * @param outputSurface The {@link Surface}.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   * @param debugViewProvider Provider for optional debug views to show intermediate output.
   * @return A configured {@code FrameEditor}.
   * @throws TransformationException If the {@code pixelWidthHeightRatio} isn't 1, reading shader
   *     files fails, or an OpenGL error occurs while creating and configuring the OpenGL
   *     components.
   */
  public static FrameEditor create(
      Context context,
      int outputWidth,
      int outputHeight,
      float pixelWidthHeightRatio,
      Matrix transformationMatrix,
      Surface outputSurface,
      boolean enableExperimentalHdrEditing,
      Transformer.DebugViewProvider debugViewProvider)
      throws TransformationException {
    if (pixelWidthHeightRatio != 1.0f) {
      // TODO(b/211782176): Consider implementing support for non-square pixels.
      throw TransformationException.createForFrameEditor(
          new UnsupportedOperationException(
              "Transformer's frame editor currently does not support frame edits on non-square"
                  + " pixels. The pixelWidthHeightRatio is: "
                  + pixelWidthHeightRatio),
          TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }

    @Nullable
    SurfaceView debugSurfaceView =
        debugViewProvider.getDebugPreviewSurfaceView(outputWidth, outputHeight);

    EGLDisplay eglDisplay;
    EGLContext eglContext;
    EGLSurface eglSurface;
    int textureId;
    GlUtil.Program glProgram;
    @Nullable EGLSurface debugPreviewEglSurface = null;
    try {
      eglDisplay = GlUtil.createEglDisplay();

      if (enableExperimentalHdrEditing) {
        eglContext = GlUtil.createEglContextEs3Rgba1010102(eglDisplay);
        // TODO(b/209404935): Don't assume BT.2020 PQ input/output.
        eglSurface = GlUtil.getEglSurfaceBt2020Pq(eglDisplay, outputSurface);
        if (debugSurfaceView != null) {
          debugPreviewEglSurface =
              GlUtil.getEglSurfaceBt2020Pq(eglDisplay, checkNotNull(debugSurfaceView.getHolder()));
        }
      } else {
        eglContext = GlUtil.createEglContext(eglDisplay);
        eglSurface = GlUtil.getEglSurface(eglDisplay, outputSurface);
        if (debugSurfaceView != null) {
          debugPreviewEglSurface =
              GlUtil.getEglSurface(eglDisplay, checkNotNull(debugSurfaceView.getHolder()));
        }
      }

      GlUtil.focusSurface(eglDisplay, eglContext, eglSurface, outputWidth, outputHeight);
      textureId = GlUtil.createExternalTexture();
      glProgram =
          configureGlProgram(
              context, transformationMatrix, textureId, enableExperimentalHdrEditing);
    } catch (IOException | GlUtil.GlException e) {
      throw TransformationException.createForFrameEditor(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }

    int debugPreviewWidth;
    int debugPreviewHeight;
    if (debugSurfaceView != null) {
      debugPreviewWidth = debugSurfaceView.getWidth();
      debugPreviewHeight = debugSurfaceView.getHeight();
    } else {
      debugPreviewWidth = C.LENGTH_UNSET;
      debugPreviewHeight = C.LENGTH_UNSET;
    }

    return new FrameEditor(
        eglDisplay,
        eglContext,
        eglSurface,
        textureId,
        glProgram,
        outputWidth,
        outputHeight,
        debugPreviewEglSurface,
        debugPreviewWidth,
        debugPreviewHeight);
  }

  private static GlUtil.Program configureGlProgram(
      Context context,
      Matrix transformationMatrix,
      int textureId,
      boolean enableExperimentalHdrEditing)
      throws IOException {
    // TODO(b/205002913): check the loaded program is consistent with the attributes
    // and uniforms expected in the code.
    String vertexShaderFilePath =
        enableExperimentalHdrEditing
            ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH
            : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        enableExperimentalHdrEditing
            ? FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH
            : FRAGMENT_SHADER_COPY_EXTERNAL_PATH;
    GlUtil.Program glProgram =
        new GlUtil.Program(context, vertexShaderFilePath, fragmentShaderFilePath);

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
    glProgram.setBufferAttribute(
        "aTexCoords", GlUtil.getTextureCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
    glProgram.setSamplerTexIdUniform("uTexSampler", textureId, /* unit= */ 0);

    if (enableExperimentalHdrEditing) {
      // In HDR editing mode the decoder output is sampled in YUV.
      glProgram.setFloatsUniform("uColorTransform", MATRIX_YUV_TO_BT2020_COLOR_TRANSFORM);
    }

    float[] transformationMatrixArray = getGlMatrixArray(transformationMatrix);
    glProgram.setFloatsUniform("uTransformationMatrix", transformationMatrixArray);
    return glProgram;
  }

  /**
   * Returns a 4x4, column-major Matrix float array, from an input {@link Matrix}. This is useful
   * for converting to the 4x4 column-major format commonly used in OpenGL.
   */
  private static float[] getGlMatrixArray(Matrix matrix) {
    float[] matrix3x3Array = new float[9];
    matrix.getValues(matrix3x3Array);
    float[] matrix4x4Array = getMatrix4x4Array(matrix3x3Array);

    // Transpose from row-major to column-major representations.
    float[] transposedMatrix4x4Array = new float[16];
    android.opengl.Matrix.transposeM(
        transposedMatrix4x4Array, /* mTransOffset= */ 0, matrix4x4Array, /* mOffset= */ 0);

    return transposedMatrix4x4Array;
  }

  /**
   * Returns a 4x4 matrix array containing the 3x3 matrix array's contents.
   *
   * <p>The 3x3 matrix array is expected to be in 2 dimensions, and the 4x4 matrix array is expected
   * to be in 3 dimensions. The output will have the third row/column's values be an identity
   * matrix's values, so that vertex transformations using this matrix will not affect the z axis.
   * <br>
   * Input format: [a, b, c, d, e, f, g, h, i] <br>
   * Output format: [a, b, 0, c, d, e, 0, f, 0, 0, 1, 0, g, h, 0, i]
   */
  private static float[] getMatrix4x4Array(float[] matrix3x3Array) {
    float[] matrix4x4Array = new float[16];
    matrix4x4Array[10] = 1;
    for (int inputRow = 0; inputRow < 3; inputRow++) {
      for (int inputColumn = 0; inputColumn < 3; inputColumn++) {
        int outputRow = (inputRow == 2) ? 3 : inputRow;
        int outputColumn = (inputColumn == 2) ? 3 : inputColumn;
        matrix4x4Array[outputRow * 4 + outputColumn] = matrix3x3Array[inputRow * 3 + inputColumn];
      }
    }
    return matrix4x4Array;
  }

  private static final String VERTEX_SHADER_TRANSFORMATION_PATH =
      "shaders/vertex_shader_transformation.glsl";
  private static final String FRAGMENT_SHADER_COPY_EXTERNAL_PATH =
      "shaders/fragment_shader_copy_external.glsl";
  private static final String VERTEX_SHADER_TRANSFORMATION_ES3_PATH =
      "shaders/vertex_shader_transformation_es3.glsl";
  private static final String FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH =
      "shaders/fragment_shader_copy_external_yuv_es3.glsl";
  // Color transform coefficients from
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/libstagefright/colorconversion/ColorConverter.cpp;l=668-670;drc=487adf977a50cac3929eba15fad0d0f461c7ff0f.
  private static final float[] MATRIX_YUV_TO_BT2020_COLOR_TRANSFORM = {
    1.168f, 1.168f, 1.168f,
    0.0f, -0.188f, 2.148f,
    1.683f, -0.652f, 0.0f,
  };

  private final float[] textureTransformMatrix;
  private final EGLDisplay eglDisplay;
  private final EGLContext eglContext;
  private final EGLSurface eglSurface;
  private final int textureId;
  private final AtomicInteger pendingInputFrameCount;
  private final AtomicInteger availableInputFrameCount;
  private final SurfaceTexture inputSurfaceTexture;
  private final Surface inputSurface;
  private final GlUtil.Program glProgram;
  private final int outputWidth;
  private final int outputHeight;
  @Nullable private final EGLSurface debugPreviewEglSurface;
  private final int debugPreviewWidth;
  private final int debugPreviewHeight;

  private boolean inputStreamEnded;

  private FrameEditor(
      EGLDisplay eglDisplay,
      EGLContext eglContext,
      EGLSurface eglSurface,
      int textureId,
      GlUtil.Program glProgram,
      int outputWidth,
      int outputHeight,
      @Nullable EGLSurface debugPreviewEglSurface,
      int debugPreviewWidth,
      int debugPreviewHeight) {
    this.eglDisplay = eglDisplay;
    this.eglContext = eglContext;
    this.eglSurface = eglSurface;
    this.textureId = textureId;
    this.glProgram = glProgram;
    this.pendingInputFrameCount = new AtomicInteger();
    this.availableInputFrameCount = new AtomicInteger();
    this.outputWidth = outputWidth;
    this.outputHeight = outputHeight;
    this.debugPreviewEglSurface = debugPreviewEglSurface;
    this.debugPreviewWidth = debugPreviewWidth;
    this.debugPreviewHeight = debugPreviewHeight;
    textureTransformMatrix = new float[16];
    inputSurfaceTexture = new SurfaceTexture(textureId);
    inputSurfaceTexture.setOnFrameAvailableListener(
        surfaceTexture -> {
          checkState(pendingInputFrameCount.getAndDecrement() > 0);
          availableInputFrameCount.incrementAndGet();
        });
    inputSurface = new Surface(inputSurfaceTexture);
  }

  /** Returns the input {@link Surface}. */
  public Surface getInputSurface() {
    return inputSurface;
  }

  /**
   * Informs the frame editor that a frame will be queued to its input surface.
   *
   * <p>Should be called before rendering a frame to the frame editor's input surface.
   *
   * @throws IllegalStateException If called after {@link #signalEndOfInputStream()}.
   */
  public void registerInputFrame() {
    checkState(!inputStreamEnded);
    pendingInputFrameCount.incrementAndGet();
  }

  /**
   * Returns whether there is available input data that can be processed by calling {@link
   * #processData()}.
   */
  public boolean canProcessData() {
    return availableInputFrameCount.get() > 0;
  }

  /**
   * Processes an input frame.
   *
   * @throws TransformationException If an OpenGL error occurs while processing the data.
   * @throws IllegalStateException If there is no input data to process. Use {@link
   *     #canProcessData()} to check whether input data is available.
   */
  public void processData() throws TransformationException {
    checkState(canProcessData());
    try {
      inputSurfaceTexture.updateTexImage();
      inputSurfaceTexture.getTransformMatrix(textureTransformMatrix);
      glProgram.setFloatsUniform("uTexTransform", textureTransformMatrix);
      glProgram.bindAttributesAndUniforms();

      focusAndDrawQuad(eglSurface, outputWidth, outputHeight);
      long presentationTimeNs = inputSurfaceTexture.getTimestamp();
      EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs);
      EGL14.eglSwapBuffers(eglDisplay, eglSurface);

      if (debugPreviewEglSurface != null) {
        focusAndDrawQuad(debugPreviewEglSurface, debugPreviewWidth, debugPreviewHeight);
        EGL14.eglSwapBuffers(eglDisplay, debugPreviewEglSurface);
      }
    } catch (GlUtil.GlException e) {
      throw TransformationException.createForFrameEditor(
          e, TransformationException.ERROR_CODE_GL_PROCESSING_FAILED);
    }
    availableInputFrameCount.decrementAndGet();
  }

  /** Releases all resources. */
  public void release() {
    glProgram.delete();
    GlUtil.deleteTexture(textureId);
    GlUtil.destroyEglContext(eglDisplay, eglContext);
    inputSurfaceTexture.release();
    inputSurface.release();
  }

  /** Focuses the specified surface with the specified width and height, then draws a quad. */
  private void focusAndDrawQuad(EGLSurface eglSurface, int width, int height) {
    GlUtil.focusSurface(eglDisplay, eglContext, eglSurface, width, height);
    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
  }

  /** Returns whether all data has been processed. */
  public boolean isEnded() {
    return inputStreamEnded
        && pendingInputFrameCount.get() == 0
        && availableInputFrameCount.get() == 0;
  }

  /** Informs the {@code FrameEditor} that no further input data should be accepted. */
  public void signalEndOfInputStream() {
    inputStreamEnded = true;
  }
}
