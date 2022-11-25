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
package com.google.android.exoplayer2.transformerdemo;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Pair;
import com.google.android.exoplayer2.effect.SingleFrameGlTextureProcessor;
import com.google.android.exoplayer2.util.FrameProcessingException;
import com.google.android.exoplayer2.util.GlProgram;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;

/**
 * A {@link SingleFrameGlTextureProcessor} that periodically dims the frames such that pixels are
 * darker the further they are away from the frame center.
 */
/* package */ final class PeriodicVignetteProcessor extends SingleFrameGlTextureProcessor {

  private static final String VERTEX_SHADER_PATH = "vertex_shader_copy_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "fragment_shader_vignette_es2.glsl";
  private static final float DIMMING_PERIOD_US = 5_600_000f;

  private final GlProgram glProgram;
  private final float minInnerRadius;
  private final float deltaInnerRadius;

  /**
   * Creates a new instance.
   *
   * <p>The inner radius of the vignette effect oscillates smoothly between {@code minInnerRadius}
   * and {@code maxInnerRadius}.
   *
   * <p>The pixels between the inner radius and the {@code outerRadius} are darkened linearly based
   * on their distance from {@code innerRadius}. All pixels outside {@code outerRadius} are black.
   *
   * <p>The parameters are given in normalized texture coordinates from 0 to 1.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param centerX The x-coordinate of the center of the effect.
   * @param centerY The y-coordinate of the center of the effect.
   * @param minInnerRadius The lower bound of the radius that is unaffected by the effect.
   * @param maxInnerRadius The upper bound of the radius that is unaffected by the effect.
   * @param outerRadius The radius after which all pixels are black.
   * @throws FrameProcessingException If a problem occurs while reading shader files.
   */
  public PeriodicVignetteProcessor(
      Context context,
      boolean useHdr,
      float centerX,
      float centerY,
      float minInnerRadius,
      float maxInnerRadius,
      float outerRadius)
      throws FrameProcessingException {
    super(useHdr);
    checkArgument(minInnerRadius <= maxInnerRadius);
    checkArgument(maxInnerRadius <= outerRadius);
    this.minInnerRadius = minInnerRadius;
    this.deltaInnerRadius = maxInnerRadius - minInnerRadius;
    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw new FrameProcessingException(e);
    }
    glProgram.setFloatsUniform("uCenter", new float[] {centerX, centerY});
    glProgram.setFloatsUniform("uOuterRadius", new float[] {outerRadius});
    // Draw the frame on the entire normalized device coordinate space, from -1 to 1, for x and y.
    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public Pair<Integer, Integer> configure(int inputWidth, int inputHeight) {
    return Pair.create(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    try {
      glProgram.use();
      glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      double theta = presentationTimeUs * 2 * Math.PI / DIMMING_PERIOD_US;
      float innerRadius =
          minInnerRadius + deltaInnerRadius * (0.5f - 0.5f * (float) Math.cos(theta));
      glProgram.setFloatsUniform("uInnerRadius", new float[] {innerRadius});
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
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
}
