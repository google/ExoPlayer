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

package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.content.Context;
import android.opengl.GLES20;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import java.io.IOException;

/**
 * Applies a {@link ColorLut} to each frame in the fragment shader.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class ColorLutShaderProgram extends SingleFrameGlShaderProgram {
  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_lut_es2.glsl";

  private final GlProgram glProgram;
  private final ColorLut colorLut;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param colorLut The {@link ColorLut} to apply to each frame in order.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public ColorLutShaderProgram(Context context, ColorLut colorLut, boolean useHdr)
      throws VideoFrameProcessingException {
    super(useHdr);
    // TODO(b/246315245): Add HDR support.
    checkArgument(!useHdr, "ColorLutShaderProgram does not support HDR colors.");
    this.colorLut = colorLut;

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }

    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    return new Size(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException {
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      glProgram.setSamplerTexIdUniform(
          "uColorLut", colorLut.getLutTextureId(presentationTimeUs), /* texUnitIndex= */ 1);
      glProgram.setFloatUniform("uColorLutLength", colorLut.getLength(presentationTimeUs));
      glProgram.bindAttributesAndUniforms();

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      colorLut.release();
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }
}
