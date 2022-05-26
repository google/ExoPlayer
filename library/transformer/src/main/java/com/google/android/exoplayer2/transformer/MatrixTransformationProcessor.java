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
import android.opengl.Matrix;
import android.util.Size;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Applies a sequence of transformation matrices in the vertex shader, and copies input pixels into
 * an output frame based on their locations after applying the sequence of transformation matrices.
 *
 * <p>Operations are done on normalized device coordinates (-1 to 1 on x, y, and z axes).
 * Transformed vertices that are moved outside of this range after any of the transformation
 * matrices are clipped to the NDC range.
 *
 * <p>The background color of the output frame will be black.
 */
@SuppressWarnings("FunctionalInterfaceClash") // b/228192298
/* package */ final class MatrixTransformationProcessor implements SingleFrameGlTextureProcessor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private static final String VERTEX_SHADER_TRANSFORMATION_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_copy_es2.glsl";
  private static final ImmutableList<float[]> NDC_SQUARE =
      ImmutableList.of(
          new float[] {-1, -1, 0, 1},
          new float[] {-1, 1, 0, 1},
          new float[] {1, 1, 0, 1},
          new float[] {1, -1, 0, 1});

  /** The {@link MatrixTransformation MatrixTransformations} to apply. */
  private final ImmutableList<GlMatrixTransformation> matrixTransformations;
  /**
   * The transformation matrices provided by the {@link MatrixTransformation MatrixTransformations}
   * for the most recent frame.
   */
  private final float[][] transformationMatrixCache;
  /**
   * The product of the {@link #transformationMatrixCache} for the most recent frame, to be applied
   * in the vertex shader.
   */
  private final float[] compositeTransformationMatrix;
  /** Matrix for storing an intermediate calculation result. */
  private final float[] tempResultMatrix;

  /**
   * A polygon in the input space chosen such that no additional clipping is needed to keep vertices
   * inside the NDC range when applying each of the {@link #matrixTransformations}.
   *
   * <p>This means that this polygon and {@link #compositeTransformationMatrix} can be used instead
   * of applying each of the {@link #matrixTransformations} to {@link #NDC_SQUARE} in separate
   * shaders.
   */
  private ImmutableList<float[]> visiblePolygon;

  private @MonotonicNonNull Size outputSize;
  private @MonotonicNonNull GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * @param matrixTransformation A {@link MatrixTransformation} that specifies the transformation
   *     matrix to use for each frame.
   */
  public MatrixTransformationProcessor(MatrixTransformation matrixTransformation) {
    this(ImmutableList.of(matrixTransformation));
  }

  /**
   * Creates a new instance.
   *
   * @param matrixTransformation A {@link GlMatrixTransformation} that specifies the transformation
   *     matrix to use for each frame.
   */
  public MatrixTransformationProcessor(GlMatrixTransformation matrixTransformation) {
    this(ImmutableList.of(matrixTransformation));
  }

  /**
   * Creates a new instance.
   *
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order.
   */
  public MatrixTransformationProcessor(
      ImmutableList<GlMatrixTransformation> matrixTransformations) {
    this.matrixTransformations = matrixTransformations;

    transformationMatrixCache = new float[matrixTransformations.size()][16];
    compositeTransformationMatrix = new float[16];
    tempResultMatrix = new float[16];
    Matrix.setIdentityM(compositeTransformationMatrix, /* smOffset= */ 0);
    visiblePolygon = NDC_SQUARE;
  }

  @Override
  public void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    outputSize = new Size(inputWidth, inputHeight);
    for (int i = 0; i < matrixTransformations.size(); i++) {
      outputSize =
          matrixTransformations.get(i).configure(outputSize.getWidth(), outputSize.getHeight());
    }

    glProgram = new GlProgram(context, VERTEX_SHADER_TRANSFORMATION_PATH, FRAGMENT_SHADER_PATH);
    glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(outputSize);
  }

  @Override
  public void drawFrame(long presentationTimeUs) throws FrameProcessingException {
    updateCompositeTransformationMatrixAndVisiblePolygon(presentationTimeUs);
    if (visiblePolygon.size() < 3) {
      return; // Need at least three visible vertices for a triangle.
    }

    try {
      checkStateNotNull(glProgram).use();
      glProgram.setFloatsUniform("uTransformationMatrix", compositeTransformationMatrix);
      glProgram.setBufferAttribute(
          "aFramePosition",
          GlUtil.createVertexBuffer(visiblePolygon),
          GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
      glProgram.bindAttributesAndUniforms();
      GLES20.glDrawArrays(
          GLES20.GL_TRIANGLE_FAN, /* first= */ 0, /* count= */ visiblePolygon.size());
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() {
    if (glProgram != null) {
      glProgram.delete();
    }
  }

  /**
   * Updates {@link #compositeTransformationMatrix} and {@link #visiblePolygon} based on the given
   * frame timestamp.
   */
  private void updateCompositeTransformationMatrixAndVisiblePolygon(long presentationTimeUs) {
    if (!updateTransformationMatrixCache(presentationTimeUs)) {
      return;
    }

    // Compute the compositeTransformationMatrix and transform and clip the visiblePolygon for each
    // MatrixTransformation's matrix.
    Matrix.setIdentityM(compositeTransformationMatrix, /* smOffset= */ 0);
    visiblePolygon = NDC_SQUARE;
    for (float[] transformationMatrix : transformationMatrixCache) {
      Matrix.multiplyMM(
          tempResultMatrix,
          /* resultOffset= */ 0,
          transformationMatrix,
          /* lhsOffset= */ 0,
          compositeTransformationMatrix,
          /* rhsOffset= */ 0);
      System.arraycopy(
          /* src= */ tempResultMatrix,
          /* srcPos= */ 0,
          /* dest= */ compositeTransformationMatrix,
          /* destPost= */ 0,
          /* length= */ tempResultMatrix.length);
      visiblePolygon =
          MatrixUtils.clipConvexPolygonToNdcRange(
              MatrixUtils.transformPoints(transformationMatrix, visiblePolygon));
      if (visiblePolygon.size() < 3) {
        // Can ignore remaining matrices as there are not enough vertices left to form a polygon.
        return;
      }
    }
    // Calculate the input frame vertices corresponding to the output frame's visible polygon.
    Matrix.invertM(
        tempResultMatrix, /* mInvOffset= */ 0, compositeTransformationMatrix, /* mOffset= */ 0);
    visiblePolygon = MatrixUtils.transformPoints(tempResultMatrix, visiblePolygon);
  }

  /**
   * Updates {@link #transformationMatrixCache} with the transformation matrices provided by the
   * {@link #matrixTransformations} for the given frame timestamp and returns whether any matrix in
   * {@link #transformationMatrixCache} changed.
   */
  private boolean updateTransformationMatrixCache(long presentationTimeUs) {
    boolean matrixChanged = false;
    for (int i = 0; i < matrixTransformations.size(); i++) {
      float[] cachedMatrix = transformationMatrixCache[i];
      float[] matrix = matrixTransformations.get(i).getGlMatrixArray(presentationTimeUs);
      if (!Arrays.equals(cachedMatrix, matrix)) {
        checkState(matrix.length == 16, "A 4x4 transformation matrix must have 16 elements");
        System.arraycopy(
            /* src= */ matrix,
            /* srcPos= */ 0,
            /* dest= */ cachedMatrix,
            /* destPost= */ 0,
            /* length= */ matrix.length);
        matrixChanged = true;
      }
    }
    return matrixChanged;
  }
}
