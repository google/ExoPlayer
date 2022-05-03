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

/** Utility functions for working with matrices. */
/* package */ class MatrixUtils {
  /**
   * Returns a 4x4, column-major {@link android.opengl.Matrix} float array, from an input {@link
   * android.graphics.Matrix}.
   *
   * <p>This is useful for converting to the 4x4 column-major format commonly used in OpenGL.
   */
  public static float[] getGlMatrixArray(android.graphics.Matrix matrix) {
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

  /** Class only contains static methods. */
  private MatrixUtils() {}
}
