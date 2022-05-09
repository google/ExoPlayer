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
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Size;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Applies a transformation matrix in the vertex shader, and copies input pixels into an output
 * frame based on their locations after applying this matrix.
 *
 * <p>Operations are done on normalized device coordinates (-1 to 1 on x and y axes).
 *
 * <p>The background color of the output frame will be black.
 */
// TODO(b/227625423): Compose multiple transformation matrices in a single shader with clipping
//  after each matrix.
@SuppressWarnings("FunctionalInterfaceClash") // b/228192298
/* package */ final class MatrixTransformationFrameProcessor implements GlFrameProcessor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private static final String VERTEX_SHADER_TRANSFORMATION_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_copy_es2.glsl";

  private final GlMatrixTransformation matrixTransformation;

  private @MonotonicNonNull Size outputSize;
  private @MonotonicNonNull GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * @param matrixTransformation A {@link MatrixTransformation} that specifies the transformation
   *     matrix to use for each frame.
   */
  public MatrixTransformationFrameProcessor(MatrixTransformation matrixTransformation) {
    this.matrixTransformation = matrixTransformation;
  }

  /**
   * Creates a new instance.
   *
   * @param matrixTransformation A {@link GlMatrixTransformation} that specifies the transformation
   *     matrix to use for each frame.
   */
  public MatrixTransformationFrameProcessor(GlMatrixTransformation matrixTransformation) {
    this.matrixTransformation = matrixTransformation;
  }

  @Override
  public void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    outputSize = matrixTransformation.configure(inputWidth, inputHeight);
    glProgram = new GlProgram(context, VERTEX_SHADER_TRANSFORMATION_PATH, FRAGMENT_SHADER_PATH);
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(outputSize);
  }

  @Override
  public void drawFrame(long presentationTimeUs) {
    checkStateNotNull(glProgram).use();
    float[] transformationMatrix = matrixTransformation.getGlMatrixArray(presentationTimeUs);
    checkState(
        transformationMatrix.length == 16, "A 4x4 transformation matrix must have 16 elements");
    glProgram.setFloatsUniform("uTransformationMatrix", transformationMatrix);
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
