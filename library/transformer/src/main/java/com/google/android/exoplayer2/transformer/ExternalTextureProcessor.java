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

/** Copies frames from an external texture and applies color transformations for HDR if needed. */
/* package */ class ExternalTextureProcessor extends SingleFrameGlTextureProcessor {

  private static final String VERTEX_SHADER_TEX_TRANSFORM_PATH =
      "shaders/vertex_shader_tex_transform_es2.glsl";
  private static final String VERTEX_SHADER_TEX_TRANSFORM_ES3_PATH =
      "shaders/vertex_shader_tex_transform_es3.glsl";
  private static final String FRAGMENT_SHADER_COPY_EXTERNAL_PATH =
      "shaders/fragment_shader_copy_external_es2.glsl";
  private static final String FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH =
      "shaders/fragment_shader_copy_external_yuv_es3.glsl";
  // Color transform coefficients from
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/av/media/libstagefright/colorconversion/ColorConverter.cpp;l=668-670;drc=487adf977a50cac3929eba15fad0d0f461c7ff0f.
  private static final float[] MATRIX_YUV_TO_BT2020_COLOR_TRANSFORM = {
    1.168f, 1.168f, 1.168f,
    0.0f, -0.188f, 2.148f,
    1.683f, -0.652f, 0.0f,
  };

  private final GlProgram glProgram;

  /**
   * Creates a new instance.
   *
   * @param useHdr Whether to process the input as an HDR signal.
   * @throws FrameProcessingException If a problem occurs while reading shader files.
   */
  public ExternalTextureProcessor(Context context, boolean useHdr) throws FrameProcessingException {
    String vertexShaderFilePath =
        useHdr ? VERTEX_SHADER_TEX_TRANSFORM_ES3_PATH : VERTEX_SHADER_TEX_TRANSFORM_PATH;
    String fragmentShaderFilePath =
        useHdr ? FRAGMENT_SHADER_COPY_EXTERNAL_YUV_ES3_PATH : FRAGMENT_SHADER_COPY_EXTERNAL_PATH;
    try {
      glProgram = new GlProgram(context, vertexShaderFilePath, fragmentShaderFilePath);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    if (useHdr) {
      // In HDR editing mode the decoder output is sampled in YUV.
      glProgram.setFloatsUniform("uColorTransform", MATRIX_YUV_TO_BT2020_COLOR_TRANSFORM);
    }
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    return new Size(inputWidth, inputHeight);
  }

  /**
   * Sets the texture transform matrix for converting an external surface texture's coordinates to
   * sampling locations.
   *
   * @param textureTransformMatrix The external surface texture's {@linkplain
   *     android.graphics.SurfaceTexture#getTransformMatrix(float[]) transform matrix}.
   */
  public void setTextureTransformMatrix(float[] textureTransformMatrix) {
    checkStateNotNull(glProgram);
    glProgram.setFloatsUniform("uTexTransform", textureTransformMatrix);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    checkStateNotNull(glProgram);
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() throws FrameProcessingException {
    super.release();
    if (glProgram != null) {
      try {
        glProgram.delete();
      } catch (GlUtil.GlException e) {
        throw new FrameProcessingException(e);
      }
    }
  }
}
