/*
 * Copyright 2024 The Android Open Source Project
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

import static androidx.media3.effect.MatrixUtils.getGlMatrixArray;

import android.content.Context;
import android.graphics.Matrix;
import androidx.annotation.RequiresApi;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/**
 * An extension of {@link SeparableConvolutionShaderProgram} that draws the sharp version of the
 * input frame on top of the output convolution.
 */
@UnstableApi
@RequiresApi(26) // See SeparableConvolutionShaderProgram.
/* package */ final class SharpSeparableConvolutionShaderProgram
    extends SeparableConvolutionShaderProgram {
  private final GlProgram sharpTransformGlProgram;
  private final float[] sharpTransformMatrixValues;

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param convolution The {@link SeparableConvolution} to apply in each direction.
   * @param scaleSharpX The scaling factor used to determine the size of the sharp image in the
   *     output frame relative to the whole output frame in the horizontal direction.
   * @param scaleSharpY The scaling factor used to determine the size of the sharp image in the
   *     output frame relative to the whole output frame in the vertical direction.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public SharpSeparableConvolutionShaderProgram(
      Context context,
      boolean useHdr,
      SeparableConvolution convolution,
      float scaleSharpX,
      float scaleSharpY)
      throws VideoFrameProcessingException {
    super(
        context,
        useHdr,
        convolution,
        /* scaleWidth= */ 1 / scaleSharpX,
        /* scaleHeight= */ 1 / scaleSharpY);
    try {
      sharpTransformGlProgram =
          new GlProgram(
              context,
              "shaders/vertex_shader_transformation_es2.glsl",
              "shaders/fragment_shader_copy_es2.glsl");
    } catch (IOException | GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
    Matrix sharpTransformMatrix = new Matrix();
    sharpTransformMatrix.setScale(scaleSharpX, scaleSharpY);
    sharpTransformMatrixValues = getGlMatrixArray(sharpTransformMatrix);
  }

  @Override
  protected void onBlurRendered(GlTextureInfo inputTexture) throws GlUtil.GlException {
    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    sharpTransformGlProgram.use();
    sharpTransformGlProgram.setSamplerTexIdUniform(
        "uTexSampler", inputTexture.texId, /* texUnitIndex= */ 0);
    sharpTransformGlProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    sharpTransformGlProgram.setFloatsUniform("uTransformationMatrix", sharpTransformMatrixValues);
    sharpTransformGlProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    sharpTransformGlProgram.bindAttributesAndUniforms();
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      sharpTransformGlProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }
}
