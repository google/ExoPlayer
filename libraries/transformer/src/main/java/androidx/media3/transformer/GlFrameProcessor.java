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

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.graphics.Matrix;
import android.opengl.GLES20;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Manages a GLSL shader program for applying a transformation matrix to a frame. */
/* package */ class GlFrameProcessor {

  static {
    GlUtil.glAssertionsEnabled = true;
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

  private final Context context;
  private final Matrix transformationMatrix;
  private final boolean enableExperimentalHdrEditing;

  private @MonotonicNonNull GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * @param context A {@link Context}.
   * @param transformationMatrix The transformation matrix to apply to each frame.
   * @param enableExperimentalHdrEditing Whether to attempt to process the input as an HDR signal.
   */
  public GlFrameProcessor(
      Context context, Matrix transformationMatrix, boolean enableExperimentalHdrEditing) {
    this.context = context;
    this.transformationMatrix = transformationMatrix;
    this.enableExperimentalHdrEditing = enableExperimentalHdrEditing;
  }

  /**
   * Does any initialization necessary such as loading and compiling a GLSL shader programs.
   *
   * <p>This method may only be called after creating the OpenGL context and focusing a render
   * target.
   */
  public void initialize() throws IOException {
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

    glProgram = new GlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
    glProgram.setBufferAttribute(
        "aTexCoords", GlUtil.getTextureCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
    if (enableExperimentalHdrEditing) {
      // In HDR editing mode the decoder output is sampled in YUV.
      glProgram.setFloatsUniform("uColorTransform", MATRIX_YUV_TO_BT2020_COLOR_TRANSFORM);
    }
    float[] transformationMatrixArray = getGlMatrixArray(transformationMatrix);
    glProgram.setFloatsUniform("uTransformationMatrix", transformationMatrixArray);
  }

  /**
   * Sets the texture transform matrix for converting an external surface texture's coordinates to
   * sampling locations.
   *
   * @param textureTransformMatrix The external surface texture's {@link
   *     android.graphics.SurfaceTexture#getTransformMatrix(float[]) transform matrix}.
   */
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    checkStateNotNull(glProgram);
    glProgram.setFloatsUniform("uTexTransform", textureTransformMatrix);
  }

  /**
   * Updates the shader program's vertex attributes and uniforms, binds them, and draws.
   *
   * <p>The frame processor must be {@link #initialize() initialized}. The caller is responsible for
   * focussing the correct render target before calling this method.
   *
   * @param inputTexId The identifier of an OpenGL texture that the fragment shader can sample from.
   */
  // TODO(b/214975934): Also pass presentationTimeNs.
  public void updateProgramAndDraw(int inputTexId) {
    checkStateNotNull(glProgram);
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* unit= */ 0);
    glProgram.use();
    glProgram.bindAttributesAndUniforms();
    GLES20.glClearColor(/* red= */ 0, /* green= */ 0, /* blue= */ 0, /* alpha= */ 0);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
  }

  /** Releases all resources. */
  public void release() {
    if (glProgram != null) {
      glProgram.delete();
    }
  }
}
