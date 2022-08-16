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

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Pair;
import androidx.media3.common.FrameProcessingException;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

/**
 * Applies a sequence of {@link RgbMatrix} to each frame.
 *
 * <p>After applying all {@link RgbMatrix} instances, color values are clamped to the limits of the
 * color space. Intermediate reults are not clamped.
 */
/* package */ final class RgbMatrixProcessor extends SingleFrameGlTextureProcessor {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH =
      "shaders/fragment_shader_transformation_es2.glsl";

  private final GlProgram glProgram;
  private final ImmutableList<RgbMatrix> rgbMatrices;

  // TODO(b/239757183): Merge RgbMatrixProcessor with MatrixTransformationProcessor.
  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param rgbMatrix The {@link RgbMatrix} to apply to each frame.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in gamma RGB BT.709.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public RgbMatrixProcessor(Context context, RgbMatrix rgbMatrix, boolean useHdr)
      throws FrameProcessingException {
    this(context, ImmutableList.of(rgbMatrix), useHdr);
  }

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param rgbMatrices The {@link RgbMatrix} to apply to each frame.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in gamma RGB BT.709.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public RgbMatrixProcessor(Context context, ImmutableList<RgbMatrix> rgbMatrices, boolean useHdr)
      throws FrameProcessingException {
    super(useHdr);
    this.rgbMatrices = rgbMatrices;

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    float[] identityMatrix = new float[16];
    Matrix.setIdentityM(identityMatrix, /* smOffset= */ 0);
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return Pair.create(inputWidth, inputHeight);
  }

  private static float[] createCompositeRgbaMatrixArray(
      ImmutableList<RgbMatrix> rgbMatrices, long presentationTimeUs) {
    float[] tempResultMatrix = new float[16];
    float[] compositeRgbaMatrix = new float[16];
    Matrix.setIdentityM(compositeRgbaMatrix, /* smOffset= */ 0);

    for (int i = 0; i < rgbMatrices.size(); i++) {
      Matrix.multiplyMM(
          /* result= */ tempResultMatrix,
          /* resultOffset= */ 0,
          /* lhs= */ rgbMatrices.get(i).getMatrix(presentationTimeUs),
          /* lhsOffset= */ 0,
          /* rhs= */ compositeRgbaMatrix,
          /* rhsOffset= */ 0);
      System.arraycopy(
          /* src= */ tempResultMatrix,
          /* srcPos= */ 0,
          /* dest= */ compositeRgbaMatrix,
          /* destPost= */ 0,
          /* length= */ tempResultMatrix.length);
    }

    return compositeRgbaMatrix;
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    // TODO(b/239431666): Add caching for compacting Matrices.
    float[] rgbMatrixArray = createCompositeRgbaMatrixArray(rgbMatrices, presentationTimeUs);
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.setFloatsUniform("uColorMatrix", rgbMatrixArray);
      glProgram.bindAttributesAndUniforms();

      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }
}
