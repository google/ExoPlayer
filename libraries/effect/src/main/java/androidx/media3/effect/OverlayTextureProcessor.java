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

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

/** Applies one or more {@link TextureOverlay}s onto each frame. */
/* package */ final class OverlayTextureProcessor extends SingleFrameGlTextureProcessor {

  private static final String VERTEX_SHADER_PATH = "shaders/vertex_shader_overlay_es2.glsl";
  private static final String FRAGMENT_SHADER_PATH = "shaders/fragment_shader_overlay_es2.glsl";
  private static final int MATRIX_OFFSET = 0;
  private static final int TRANSPARENT_TEXTURE_WIDTH_HEIGHT = 1;

  private final GlProgram glProgram;
  private final ImmutableList<TextureOverlay> overlays;
  private final float[] aspectRatioMatrix;
  private final float[] overlayMatrix;

  private int videoWidth;
  private int videoHeight;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @throws FrameProcessingException If a problem occurs while reading shader files.
   */
  public OverlayTextureProcessor(
      Context context, boolean useHdr, ImmutableList<TextureOverlay> overlays)
      throws FrameProcessingException {
    super(useHdr);
    checkArgument(!useHdr, "OverlayTextureProcessor does not support HDR colors yet.");
    checkArgument(
        overlays.size() <= 1,
        "OverlayTextureProcessor does not support multiple overlays in the same processor yet.");
    this.overlays = overlays;
    aspectRatioMatrix = GlUtil.create4x4IdentityMatrix();
    overlayMatrix = GlUtil.create4x4IdentityMatrix();

    try {
      glProgram = new GlProgram(context, VERTEX_SHADER_PATH, FRAGMENT_SHADER_PATH);
    } catch (GlUtil.GlException | IOException e) {
      throw new FrameProcessingException(e);
    }

    glProgram.setBufferAttribute(
        "aFramePosition",
        GlUtil.getNormalizedCoordinateBounds(),
        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE);
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    videoWidth = inputWidth;
    videoHeight = inputHeight;
    return new Size(inputWidth, inputHeight);
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs) throws FrameProcessingException {
    try {
      glProgram.use();
      if (!overlays.isEmpty()) {
        TextureOverlay overlay = overlays.get(0);
        glProgram.setSamplerTexIdUniform(
            "uOverlayTexSampler1", overlay.getTextureId(presentationTimeUs), /* texUnitIndex= */ 1);
        Size overlayTextureSize = overlay.getTextureSize(presentationTimeUs);
        GlUtil.setToIdentity(aspectRatioMatrix);
        Matrix.scaleM(
            aspectRatioMatrix,
            MATRIX_OFFSET,
            videoWidth / (float) overlayTextureSize.getWidth(),
            videoHeight / (float) overlayTextureSize.getHeight(),
            /* z= */ 1);
        glProgram.setFloatsUniform("uAspectRatioMatrix", aspectRatioMatrix);
        Matrix.invertM(
            overlayMatrix,
            MATRIX_OFFSET,
            overlay.getOverlaySettings(presentationTimeUs).matrix,
            MATRIX_OFFSET);
        glProgram.setFloatsUniform("uOverlayMatrix", overlayMatrix);
        glProgram.setFloatUniform(
            "uOverlayAlpha1", overlay.getOverlaySettings(presentationTimeUs).alpha);

      } else {
        glProgram.setSamplerTexIdUniform(
            "uOverlayTexSampler1", createTransparentTexture(), /* texUnitIndex= */ 1);
      }
      glProgram.setSamplerTexIdUniform("uVideoTexSampler0", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e, presentationTimeUs);
    }
  }

  private int createTransparentTexture() throws FrameProcessingException {
    try {
      int textureId =
          GlUtil.createTexture(
              TRANSPARENT_TEXTURE_WIDTH_HEIGHT,
              TRANSPARENT_TEXTURE_WIDTH_HEIGHT,
              /* useHighPrecisionColorComponents= */ false);
      GlUtil.checkGlError();
      return textureId;
    } catch (GlUtil.GlException e) {
      throw new FrameProcessingException(e);
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
