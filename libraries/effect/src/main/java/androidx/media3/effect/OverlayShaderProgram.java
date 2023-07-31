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
import android.util.Pair;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;

/** Applies zero or more {@link TextureOverlay}s onto each frame. */
/* package */ final class OverlayShaderProgram extends SingleFrameGlShaderProgram {

  private static final int MATRIX_OFFSET = 0;

  private final GlProgram glProgram;
  private final ImmutableList<TextureOverlay> overlays;
  private final float[] videoFrameAnchorMatrix;
  private final float[] videoFrameAnchorMatrixInv;
  private final float[] aspectRatioMatrix;
  private final float[] scaleMatrix;
  private final float[] scaleMatrixInv;
  private final float[] overlayAnchorMatrix;
  private final float[] overlayAnchorMatrixInv;
  private final float[] rotateMatrix;
  private final float[] overlayAspectRatioMatrix;
  private final float[] overlayAspectRatioMatrixInv;
  private final float[] transformationMatrix;

  private int videoWidth;
  private int videoHeight;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @throws VideoFrameProcessingException If a problem occurs while reading shader files.
   */
  public OverlayShaderProgram(
      Context context, boolean useHdr, ImmutableList<TextureOverlay> overlays)
      throws VideoFrameProcessingException {
    super(/* useHighPrecisionColorComponents= */ useHdr);
    checkArgument(!useHdr, "OverlayShaderProgram does not support HDR colors yet.");
    // The maximum number of samplers allowed in a single GL program is 16.
    // We use one for every overlay and one for the video.
    checkArgument(
        overlays.size() <= 15,
        "OverlayShaderProgram does not support more than 15 overlays in the same instance.");
    this.overlays = overlays;
    aspectRatioMatrix = GlUtil.create4x4IdentityMatrix();
    videoFrameAnchorMatrix = GlUtil.create4x4IdentityMatrix();
    videoFrameAnchorMatrixInv = GlUtil.create4x4IdentityMatrix();
    overlayAnchorMatrix = GlUtil.create4x4IdentityMatrix();
    overlayAnchorMatrixInv = GlUtil.create4x4IdentityMatrix();
    rotateMatrix = GlUtil.create4x4IdentityMatrix();
    scaleMatrix = GlUtil.create4x4IdentityMatrix();
    scaleMatrixInv = GlUtil.create4x4IdentityMatrix();
    overlayAspectRatioMatrix = GlUtil.create4x4IdentityMatrix();
    overlayAspectRatioMatrixInv = GlUtil.create4x4IdentityMatrix();
    transformationMatrix = GlUtil.create4x4IdentityMatrix();
    try {
      glProgram =
          new GlProgram(createVertexShader(overlays.size()), createFragmentShader(overlays.size()));
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
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
    Size videoSize = new Size(inputWidth, inputHeight);
    for (TextureOverlay overlay : overlays) {
      overlay.configure(videoSize);
    }
    return videoSize;
  }

  @Override
  public void drawFrame(int inputTexId, long presentationTimeUs)
      throws VideoFrameProcessingException {
    try {
      glProgram.use();
      if (!overlays.isEmpty()) {
        for (int texUnitIndex = 1; texUnitIndex <= overlays.size(); texUnitIndex++) {
          TextureOverlay overlay = overlays.get(texUnitIndex - 1);

          glProgram.setSamplerTexIdUniform(
              Util.formatInvariant("uOverlayTexSampler%d", texUnitIndex),
              overlay.getTextureId(presentationTimeUs),
              texUnitIndex);

          GlUtil.setToIdentity(aspectRatioMatrix);
          GlUtil.setToIdentity(videoFrameAnchorMatrix);
          GlUtil.setToIdentity(videoFrameAnchorMatrixInv);
          GlUtil.setToIdentity(overlayAnchorMatrix);
          GlUtil.setToIdentity(overlayAnchorMatrixInv);
          GlUtil.setToIdentity(scaleMatrix);
          GlUtil.setToIdentity(scaleMatrixInv);
          GlUtil.setToIdentity(rotateMatrix);
          GlUtil.setToIdentity(overlayAspectRatioMatrix);
          GlUtil.setToIdentity(overlayAspectRatioMatrixInv);
          GlUtil.setToIdentity(transformationMatrix);

          // Anchor point of overlay within output frame.
          Pair<Float, Float> videoFrameAnchor =
              overlay.getOverlaySettings(presentationTimeUs).videoFrameAnchor;
          Matrix.translateM(
              videoFrameAnchorMatrix,
              MATRIX_OFFSET,
              videoFrameAnchor.first,
              videoFrameAnchor.second,
              /* z= */ 0f);
          Matrix.invertM(
              videoFrameAnchorMatrixInv, MATRIX_OFFSET, videoFrameAnchorMatrix, MATRIX_OFFSET);

          Matrix.scaleM(
              aspectRatioMatrix,
              MATRIX_OFFSET,
              videoWidth / (float) overlay.getTextureSize(presentationTimeUs).getWidth(),
              videoHeight / (float) overlay.getTextureSize(presentationTimeUs).getHeight(),
              /* z= */ 1f);

          // Scale the image.
          Pair<Float, Float> scale = overlay.getOverlaySettings(presentationTimeUs).scale;
          Matrix.scaleM(
              scaleMatrix,
              MATRIX_OFFSET,
              scaleMatrix,
              MATRIX_OFFSET,
              scale.first,
              scale.second,
              /* z= */ 1f);
          Matrix.invertM(scaleMatrixInv, MATRIX_OFFSET, scaleMatrix, MATRIX_OFFSET);

          // Translate the overlay within its frame.
          Pair<Float, Float> overlayAnchor =
              overlay.getOverlaySettings(presentationTimeUs).overlayAnchor;
          Matrix.translateM(
              overlayAnchorMatrix,
              MATRIX_OFFSET,
              overlayAnchor.first,
              overlayAnchor.second,
              /* z= */ 0f);
          Matrix.invertM(overlayAnchorMatrixInv, MATRIX_OFFSET, overlayAnchorMatrix, MATRIX_OFFSET);

          // Rotate the image.
          Matrix.rotateM(
              rotateMatrix,
              MATRIX_OFFSET,
              rotateMatrix,
              MATRIX_OFFSET,
              overlay.getOverlaySettings(presentationTimeUs).rotationDegrees,
              /* x= */ 0f,
              /* y= */ 0f,
              /* z= */ 1f);
          Matrix.invertM(rotateMatrix, MATRIX_OFFSET, rotateMatrix, MATRIX_OFFSET);

          // Rotation matrix needs to account for overlay aspect ratio to prevent stretching.
          Matrix.scaleM(
              overlayAspectRatioMatrix,
              MATRIX_OFFSET,
              (float) overlay.getTextureSize(presentationTimeUs).getHeight()
                  / (float) overlay.getTextureSize(presentationTimeUs).getWidth(),
              /* y= */ 1f,
              /* z= */ 1f);
          Matrix.invertM(
              overlayAspectRatioMatrixInv, MATRIX_OFFSET, overlayAspectRatioMatrix, MATRIX_OFFSET);

          // Rotation needs to be agnostic of the scaling matrix and the aspect ratios.
          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              scaleMatrixInv,
              MATRIX_OFFSET);

          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              overlayAspectRatioMatrix,
              MATRIX_OFFSET);

          // Rotation matrix.
          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              rotateMatrix,
              MATRIX_OFFSET);

          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              overlayAspectRatioMatrixInv,
              MATRIX_OFFSET);

          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              scaleMatrix,
              MATRIX_OFFSET);

          // Translate image.
          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              overlayAnchorMatrixInv,
              MATRIX_OFFSET);

          // Scale image.
          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              scaleMatrixInv,
              MATRIX_OFFSET);

          // Correct for aspect ratio of image in output frame.
          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              aspectRatioMatrix,
              MATRIX_OFFSET);

          // Anchor position in output frame.
          Matrix.multiplyMM(
              transformationMatrix,
              MATRIX_OFFSET,
              transformationMatrix,
              MATRIX_OFFSET,
              videoFrameAnchorMatrixInv,
              MATRIX_OFFSET);

          glProgram.setFloatsUniform(
              Util.formatInvariant("uTransformationMatrix%d", texUnitIndex), transformationMatrix);

          glProgram.setFloatUniform(
              Util.formatInvariant("uOverlayAlpha%d", texUnitIndex),
              overlay.getOverlaySettings(presentationTimeUs).alpha);
        }
      }
      glProgram.setSamplerTexIdUniform("uVideoTexSampler0", inputTexId, /* texUnitIndex= */ 0);
      glProgram.bindAttributesAndUniforms();
      // The four-vertex triangle strip forms a quad.
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4);
      GlUtil.checkGlError();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e, presentationTimeUs);
    }
  }

  @Override
  public void release() throws VideoFrameProcessingException {
    super.release();
    try {
      glProgram.delete();
    } catch (GlUtil.GlException e) {
      throw new VideoFrameProcessingException(e);
    }
  }

  private static String createVertexShader(int numOverlays) {
    StringBuilder shader =
        new StringBuilder()
            .append("#version 100\n")
            .append("attribute vec4 aFramePosition;\n")
            .append("varying vec2 vVideoTexSamplingCoord0;\n");

    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader
          .append(Util.formatInvariant("uniform mat4 uTransformationMatrix%s;\n", texUnitIndex))
          .append(Util.formatInvariant("varying vec2 vOverlayTexSamplingCoord%s;\n", texUnitIndex));
    }

    shader
        .append("vec2 getTexSamplingCoord(vec2 ndcPosition){\n")
        .append("  return vec2(ndcPosition.x * 0.5 + 0.5, ndcPosition.y * 0.5 + 0.5);\n")
        .append("}\n")
        .append("void main() {\n")
        .append("  gl_Position = aFramePosition;\n")
        .append("  vVideoTexSamplingCoord0 = getTexSamplingCoord(aFramePosition.xy);\n");

    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader
          .append(Util.formatInvariant("  vec4 aOverlayPosition%d = \n", texUnitIndex))
          .append(
              Util.formatInvariant("  uTransformationMatrix%s * aFramePosition;\n", texUnitIndex))
          .append(
              Util.formatInvariant(
                  "  vOverlayTexSamplingCoord%d = getTexSamplingCoord(aOverlayPosition%d.xy);\n",
                  texUnitIndex, texUnitIndex));
    }

    shader.append("}\n");

    return shader.toString();
  }

  private static String createFragmentShader(int numOverlays) {
    StringBuilder shader =
        new StringBuilder()
            .append("#version 100\n")
            .append("precision mediump float;\n")
            .append("uniform sampler2D uVideoTexSampler0;\n")
            .append("varying vec2 vVideoTexSamplingCoord0;\n")
            .append("// Manually implementing the CLAMP_TO_BORDER texture wrapping option\n")
            .append(
                "// (https://open.gl/textures) since it's not implemented until OpenGL ES 3.2.\n")
            .append("vec4 getClampToBorderOverlayColor(\n")
            .append("    sampler2D texSampler, vec2 texSamplingCoord, float alpha){\n")
            .append("  if (texSamplingCoord.x > 1.0 || texSamplingCoord.x < 0.0\n")
            .append("      || texSamplingCoord.y > 1.0 || texSamplingCoord.y < 0.0) {\n")
            .append("    return vec4(0.0, 0.0, 0.0, 0.0);\n")
            .append("  } else {\n")
            .append("    vec4 overlayColor = vec4(texture2D(texSampler, texSamplingCoord));\n")
            .append("    overlayColor.a = alpha * overlayColor.a;\n")
            .append("    return overlayColor;\n")
            .append("  }\n")
            .append("}\n")
            .append("\n")
            .append("float getMixAlpha(float videoAlpha, float overlayAlpha) {\n")
            .append("  if (videoAlpha == 0.0) {\n")
            .append("    return 1.0;\n")
            .append("  } else {\n")
            .append("    return clamp(overlayAlpha/videoAlpha, 0.0, 1.0);\n")
            .append("  }\n")
            .append("}\n")
            .append("")
            .append("float srgbEotfSingleChannel(float srgb) {\n")
            .append("  return srgb <= 0.04045 ? srgb / 12.92 : pow((srgb + 0.055) / 1.055, 2.4);\n")
            .append("}\n")
            .append("// sRGB EOTF.\n")
            .append("vec3 applyEotf(const vec3 srgb) {\n")
            .append("// Reference implementation:\n")
            .append(
                "// https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa;l=235\n")
            .append("  return vec3(\n")
            .append("    srgbEotfSingleChannel(srgb.r),\n")
            .append("    srgbEotfSingleChannel(srgb.g),\n")
            .append("    srgbEotfSingleChannel(srgb.b)\n")
            .append("  );\n")
            .append("}\n");

    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader
          .append(Util.formatInvariant("uniform sampler2D uOverlayTexSampler%d;\n", texUnitIndex))
          .append(Util.formatInvariant("uniform float uOverlayAlpha%d;\n", texUnitIndex))
          .append(Util.formatInvariant("varying vec2 vOverlayTexSamplingCoord%d;\n", texUnitIndex));
    }

    shader
        .append("void main() {\n")
        .append(
            "  vec4 videoColor = vec4(texture2D(uVideoTexSampler0, vVideoTexSamplingCoord0));\n")
        .append("  vec4 fragColor = videoColor;\n");

    for (int texUnitIndex = 1; texUnitIndex <= numOverlays; texUnitIndex++) {
      shader
          .append(
              Util.formatInvariant(
                  "  vec4 electricalOverlayColor%d = getClampToBorderOverlayColor(\n",
                  texUnitIndex))
          .append(
              Util.formatInvariant(
                  "    uOverlayTexSampler%d, vOverlayTexSamplingCoord%d, uOverlayAlpha%d);\n",
                  texUnitIndex, texUnitIndex, texUnitIndex))
          .append(Util.formatInvariant("  vec4 opticalOverlayColor%d = vec4(\n", texUnitIndex))
          .append(
              Util.formatInvariant(
                  "    applyEotf(electricalOverlayColor%d.rgb), electricalOverlayColor%d.a);\n",
                  texUnitIndex, texUnitIndex))
          .append("  fragColor = mix(\n")
          .append(
              Util.formatInvariant(
                  "    fragColor, opticalOverlayColor%d, getMixAlpha(videoColor.a,"
                      + " opticalOverlayColor%d.a));\n",
                  texUnitIndex, texUnitIndex));
    }

    shader.append("  gl_FragColor = fragColor;\n").append("}\n");

    return shader.toString();
  }
}
