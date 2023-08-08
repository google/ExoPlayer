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
import androidx.annotation.CallSuper;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import java.io.IOException;

/**
 * Manages a pool of {@linkplain GlTextureInfo textures}, and caches the input frame.
 *
 * <p>Implements {@link FrameCache}.
 */
/* package */ class FrameCacheGlShaderProgram extends BaseGlShaderProgram {
  private static final String VERTEX_SHADER_TRANSFORMATION_ES2_PATH =
      "shaders/vertex_shader_transformation_es2.glsl";
  private static final String FRAGMENT_SHADER_TRANSFORMATION_ES2_PATH =
      "shaders/fragment_shader_transformation_es2.glsl";

  private final GlProgram copyProgram;

  /** Creates a new instance. */
  public FrameCacheGlShaderProgram(Context context, int capacity, boolean useHdr)
      throws VideoFrameProcessingException {
    super(/* useHighPrecisionColorComponents= */ useHdr, capacity);

    try {
      this.copyProgram =
          new GlProgram(
              context,
              VERTEX_SHADER_TRANSFORMATION_ES2_PATH,
              FRAGMENT_SHADER_TRANSFORMATION_ES2_PATH);
    } catch (IOException | GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }

    float[] identityMatrix = GlUtil.create4x4IdentityMatrix();
    copyProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix);
    copyProgram.setFloatsUniform("uTransformationMatrix", identityMatrix);
    copyProgram.setFloatsUniform("uRgbMatrix", identityMatrix);
    copyProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    return new Size(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException {
    try {
      copyProgram.use();
      copyProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0);
      copyProgram.bindAttributesAndUniforms();
      GLES20.glDrawArrays(
          GLES20.GL_TRIANGLE_STRIP,
          /* first= */ 0,
          /* count= */ GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
    } catch (GlUtil.GlException e) {
      throw VideoFrameProcessingException.from(e);
    }
  }

  @Override
  @CallSuper
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      copyProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }
}
