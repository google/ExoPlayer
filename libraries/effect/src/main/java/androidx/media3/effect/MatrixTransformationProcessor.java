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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;

/**
 * Applies a sequence of transformation matrices in the vertex shader, and copies input pixels into
 * an output frame based on their locations after applying the sequence of transformation matrices.
 *
 * <p>Operations are done on normalized device coordinates (-1 to 1 on x, y, and z axes).
 * Transformed vertices that are moved outside of this range after any of the transformation
 * matrices are clipped to the NDC range.
 *
 * <p>The background color of the output frame will be (r=0, g=0, b=0, a=0).
 *
 * <p>Can copy frames from an external texture and apply color transformations for HDR if needed.
 */
@UnstableApi
@SuppressWarnings("FunctionalInterfaceClash") // b/228192298
/* package */ final class MatrixTransformationProcessor extends SingleFrameGlTextureProcessor
    implements ExternalTextureProcessor {

  private static final String VERTEX_SHADER_TRANSFORMATION_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String VERTEX_SHADER_TRANSFORMATION_ES3_PATH =
      "shaders/vertex_shader_transformation_es3.glsl";
  private static final String FRAGMENT_SHADER_COPY_PATH = "shaders/fragment_shader_copy_es2.glsl";
  private static final String FRAGMENT_SHADER_OETF_ES3_PATH =
      "shaders/fragment_shader_oetf_es3.glsl";
  private static final String FRAGMENT_SHADER_COPY_EXTERNAL_PATH =
      "shaders/fragment_shader_copy_external_es2.glsl";
  private static final String FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH =
      "shaders/fragment_shader_copy_external_yuv_es3.glsl";
  private static final ImmutableList<float[]> NDC_SQUARE =
      ImmutableList.of(
          new float[] {-1, -1, 0, 1},
          new float[] {-1, 1, 0, 1},
          new float[] {1, 1, 0, 1},
          new float[] {1, -1, 0, 1});

  // YUV to RGB color transform coefficients can be calculated from the BT.2020 specification, by
  // inverting the RGB to YUV equations, and scaling for limited range.
  // https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.2020-2-201510-I!!PDF-E.pdf
  private static final float[] BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX = {
    1.0000f, 1.0000f, 1.0000f,
    0.0000f, -0.1646f, 1.8814f,
    1.4746f, -0.5714f, 0.0000f
  };
  private static final float[] BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX = {
    1.1689f, 1.1689f, 1.1689f,
    0.0000f, -0.1881f, 2.1502f,
    1.6853f, -0.6530f, 0.0000f,
  };

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

  private final GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * <p>Input and output are both intermediate optical colors, which are linear RGB BT.2020 if
   * {@code useHdr} is {@code true} and gamma RGB BT.709 if not.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order.
   * @param useHdr Whether input and output colors are HDR.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTransformationProcessor create(
      Context context, ImmutableList<GlMatrixTransformation> matrixTransformations, boolean useHdr)
      throws FrameProcessingException {
    GlProgram glProgram =
        createGlProgram(context, VERTEX_SHADER_TRANSFORMATION_PATH, FRAGMENT_SHADER_COPY_PATH);

    // No transfer functions needed, because input and output are both optical colors.
    return new MatrixTransformationProcessor(glProgram, matrixTransformations, useHdr);
  }

  /**
   * Creates a new instance.
   *
   * <p>Input will be sampled from an external texture. The caller should use {@link
   * #setTextureTransformMatrix(float[])} to provide the transformation matrix associated with the
   * external texture.
   *
   * <p>Applies the {@code electricalColorInfo} EOTF to convert from electrical color input, to
   * intermediate optical {@link GlTextureProcessor} color output, before {@code
   * matrixTransformations} are applied.
   *
   * <p>Intermediate optical colors are linear RGB BT.2020 if {@code electricalColorInfo} is
   * {@linkplain ColorInfo#isTransferHdr(ColorInfo) HDR}, and gamma RGB BT.709 if not.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order.
   * @param electricalColorInfo The electrical {@link ColorInfo} describing input colors.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTransformationProcessor createWithExternalSamplerApplyingEotf(
      Context context,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      ColorInfo electricalColorInfo)
      throws FrameProcessingException {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        useHdr ? FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH : FRAGMENT_SHADER_COPY_EXTERNAL_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    // TODO(b/241902517): Implement gamma transfer functions.
    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      if (!GlUtil.isYuvTargetExtensionSupported()) {
        throw new FrameProcessingException(
            "The EXT_YUV_target extension is required for HDR editing input.");
      }
      glProgram.setFloatsUniform(
          "uYuvToRgbColorTransform",
          electricalColorInfo.colorRange == C.COLOR_RANGE_FULL
              ? BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX
              : BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX);

      @C.ColorTransfer int colorTransfer = electricalColorInfo.colorTransfer;
      checkArgument(
          colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084);
      glProgram.setIntUniform("uEotfColorTransfer", colorTransfer);
    }

    return new MatrixTransformationProcessor(glProgram, matrixTransformations, useHdr);
  }

  /**
   * Creates a new instance.
   *
   * <p>Applies the {@code electricalColorInfo} OETF to convert from intermediate optical {@link
   * GlTextureProcessor} color input, to electrical color output, after {@code
   * matrixTransformations} are applied.
   *
   * <p>Intermediate optical colors are linear RGB BT.2020 if {@code electricalColorInfo} is
   * {@linkplain ColorInfo#isTransferHdr(ColorInfo) HDR}, and gamma RGB BT.709 if not.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order.
   * @param electricalColorInfo The electrical {@link ColorInfo} describing output colors.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTransformationProcessor createApplyingOetf(
      Context context,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      ColorInfo electricalColorInfo)
      throws FrameProcessingException {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        useHdr ? FRAGMENT_SHADER_OETF_ES3_PATH : FRAGMENT_SHADER_COPY_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    // TODO(b/241902517): Implement gamma transfer functions.
    if (useHdr) {
      @C.ColorTransfer int colorTransfer = electricalColorInfo.colorTransfer;
      checkArgument(
          colorTransfer == C.COLOR_TRANSFER_HLG || colorTransfer == C.COLOR_TRANSFER_ST2084);
      glProgram.setIntUniform("uOetfColorTransfer", colorTransfer);
    }

    return new MatrixTransformationProcessor(glProgram, matrixTransformations, useHdr);
  }

  /**
   * Creates a new instance.
   *
   * <p>Input will be sampled from an external texture. The caller should use {@link
   * #setTextureTransformMatrix(float[])} to provide the transformation matrix associated with the
   * external texture.
   *
   * <p>Applies the OETF, {@code matrixTransformations}, then the EOTF, to convert from and to input
   * and output electrical colors.
   *
   * @param context The {@link Context}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order.
   * @param electricalColorInfo The electrical {@link ColorInfo} describing input and output colors.
   * @throws FrameProcessingException If a problem occurs while reading shader files or an OpenGL
   *     operation fails or is unsupported.
   */
  public static MatrixTransformationProcessor createWithExternalSamplerApplyingEotfThenOetf(
      Context context,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      ColorInfo electricalColorInfo)
      throws FrameProcessingException {
    boolean useHdr = ColorInfo.isTransferHdr(electricalColorInfo);
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TRANSFORMATION_ES3_PATH : VERTEX_SHADER_TRANSFORMATION_PATH;
    String fragmentShaderFilePath =
        useHdr ? FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH : FRAGMENT_SHADER_COPY_EXTERNAL_PATH;
    GlProgram glProgram = createGlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);

    // TODO(b/241902517): Implement gamma transfer functions.
    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      if (!GlUtil.isYuvTargetExtensionSupported()) {
        throw new FrameProcessingException(
            "The EXT_YUV_target extension is required for HDR editing input.");
      }
      glProgram.setFloatsUniform(
          "uYuvToRgbColorTransform",
          electricalColorInfo.colorRange == C.COLOR_RANGE_FULL
              ? BT2020_FULL_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX
              : BT2020_LIMITED_RANGE_YUV_TO_RGB_COLOR_TRANSFORM_MATRIX);

      // No transfer functions needed, because the EOTF and OETF cancel out.
      glProgram.setIntUniform("uEotfColorTransfer", Format.NO_VALUE);
    }

    return new MatrixTransformationProcessor(glProgram, matrixTransformations, useHdr);
  }

  /**
   * Creates a new instance.
   *
   * @param glProgram The {@link GlProgram}.
   * @param matrixTransformations The {@link GlMatrixTransformation GlMatrixTransformations} to
   *     apply to each frame in order.
   * @param useHdr Whether to process the input as an HDR signal. Using HDR requires the {@code
   *     EXT_YUV_target} OpenGL extension.
   */
  private MatrixTransformationProcessor(
      GlProgram glProgram,
      ImmutableList<GlMatrixTransformation> matrixTransformations,
      boolean useHdr) {
    super(useHdr);
    this.glProgram = glProgram;
    this.matrixTransformations = matrixTransformations;

    transformationMatrixCache = new float[matrixTransformations.size()][16];
    compositeTransformationMatrix = new float[16];
    Matrix.setIdentityM(compositeTransformationMatrix, /* smOffset= */ 0);
    tempResultMatrix = new float[16];
    visiblePolygon = NDC_SQUARE;
  }

  private static GlProgram createGlProgram(
      Context context, String vertexShaderFilePath, String fragmentShaderFilePath)
      throws FrameProcessingException {

    GlProgram glProgram;
    try {
      glProgram = new GlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }

    float[] identityMatrix = new float[16];
    Matrix.setIdentityM(identityMatrix, /* smOffset= */ 0);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    return glProgram;
  }

  @Override
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    glProgram.setFloatsUniform("uTexTransformationMatrix", textureTransformMatrix);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return MatrixUtils.configureAndGetOutputSize(inputWidth, inputHeight, matrixTransformations);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    updateCompositeTransformationMatrixAndVisiblePolygon(presentationTimeUs);
    if (visiblePolygon.size() < 3) {
      return; // Need at least three visible vertices for a triangle.
    }

    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
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
  public void release() throws FrameProcessingException {
    super.release();
    try {
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e);
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
          /* result= */ tempResultMatrix,
          /* resultOffset= */ 0,
          /* lhs= */ transformationMatrix,
          /* lhsOffset= */ 0,
          /* rhs= */ compositeTransformationMatrix,
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
