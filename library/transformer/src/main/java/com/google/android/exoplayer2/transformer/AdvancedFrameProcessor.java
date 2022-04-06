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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Size;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Applies a transformation matrix in the vertex shader.
 *
 * <p>Operations are done on normalized device coordinates (-1 to 1 on x and y axes). No automatic
 * adjustments (like done in {@link ScaleToFitFrameProcessor}) are applied on the transformation.
 * Width and height are not modified. The background color will default to black.
 */
@SuppressWarnings("FunctionalInterfaceClash") // b/228192298
public final class AdvancedFrameProcessor implements GlFrameProcessor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  /** Updates the transformation {@link android.opengl.Matrix} for each frame. */
  public interface GlMatrixProvider {
    /**
     * Updates the transformation {@link android.opengl.Matrix} to apply to the frame with the given
     * timestamp in place.
     */
    float[] getGlMatrixArray(long presentationTimeUs);
  }

  /** Provides a {@link android.graphics.Matrix} for each frame. */
  public interface MatrixProvider extends GlMatrixProvider {
    /**
     * Returns the transformation {@link android.graphics.Matrix} to apply to the frame with the
     * given timestamp.
     */
    android.graphics.Matrix getMatrix(long presentationTimeUs);

    @Override
    default float[] getGlMatrixArray(long presentationTimeUs) {
      return AdvancedFrameProcessor.getGlMatrixArray(getMatrix(presentationTimeUs));
    }
  }

  private static final String VERTEX_SHADER_TRANSFORMATION_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_copy_es2.glsl";

  /**
   * Returns a 4x4, column-major {@link android.opengl.Matrix} float array, from an input {@link
   * android.graphics.Matrix}.
   *
   * <p>This is useful for converting to the 4x4 column-major format commonly used in OpenGL.
   */
  private static float[] getGlMatrixArray(android.graphics.Matrix matrix) {
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

  private final Context context;
  private final GlMatrixProvider matrixProvider;

  private @MonotonicNonNull Size size;
  private @MonotonicNonNull GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param transformationMatrix The transformation {@link android.graphics.Matrix} to apply to each
   *     frame. Operations are done on normalized device coordinates (-1 to 1 on x and y), and no
   *     automatic adjustments are applied on the transformation matrix.
   */
  public AdvancedFrameProcessor(Context context, android.graphics.Matrix transformationMatrix) {
    this(context, getGlMatrixArray(transformationMatrix));
  }

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param matrixProvider A {@link MatrixProvider} that provides the transformation matrix to apply
   *     to each frame.
   */
  public AdvancedFrameProcessor(Context context, MatrixProvider matrixProvider) {
    this.context = context;
    this.matrixProvider = matrixProvider;
  }

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param transformationMatrix The 4x4 transformation {@link android.opengl.Matrix} to apply to
   *     each frame. Operations are done on normalized device coordinates (-1 to 1 on x and y), and
   *     no automatic adjustments are applied on the transformation matrix.
   */
  public AdvancedFrameProcessor(Context context, float[] transformationMatrix) {
    this(context, /* matrixProvider= */ (long presentationTimeUs) -> transformationMatrix.clone());
    checkArgument(
        transformationMatrix.length == 16, "A 4x4 transformation matrix must have 16 elements.");
  }

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param matrixProvider A {@link GlMatrixProvider} that updates the transformation matrix for
   *     each frame.
   */
  public AdvancedFrameProcessor(Context context, GlMatrixProvider matrixProvider) {
    this.context = context;
    this.matrixProvider = matrixProvider;
  }

  @Override
  public void initialize(int inputTexId, int inputWidth, int inputHeight) throws IOException {
    size = new Size(inputWidth, inputHeight);
    // TODO(b/205002913): check the loaded program is consistent with the attributes and uniforms
    //  expected in the code.
    glProgram = new GlProgram(context, VERTEX_SHADER_TRANSFORMATION_PATH, FRAGMENT_SHADER_PATH);
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
    glProgram.setBufferAttribute(
        "aTexSamplingCoord", GlUtil.getTextureCoordinateBounds(), GlUtil.RECTANGLE_VERTICES_COUNT);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(size);
  }

  @Override
  public void updateProgramAndDraw(long presentationTimeUs) {
    checkStateNotNull(glProgram).use();
    glProgram.setFloatsUniform(
        "uTransformationMatrix", matrixProvider.getGlMatrixArray(presentationTimeUs));
    glProgram.bindAttributesAndUniforms();
    // The four-vertex triangle strip forms a quad.
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    GlUtil.checkGlError();
  }

  @Override
  public void release() {
    if (glProgram != null) {
      glProgram.delete();
    }
  }
}
