/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.opengl.Matrix;
import android.util.Pair;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Provides a matrix for {@link OverlaySettings}, to be applied on a vertex. */
/* package */ class OverlayMatrixProvider {
  protected static final int MATRIX_OFFSET = 0;
  private final float[] backgroundFrameAnchorMatrix;
  private final float[] aspectRatioMatrix;
  private final float[] scaleMatrix;
  private final float[] scaleMatrixInv;
  private final float[] overlayFrameAnchorMatrix;
  private final float[] rotateMatrix;
  private final float[] overlayAspectRatioMatrix;
  private final float[] overlayAspectRatioMatrixInv;
  private final float[] transformationMatrix;
  private @MonotonicNonNull Size backgroundSize;

  public OverlayMatrixProvider() {
    aspectRatioMatrix = GlUtil.create4x4IdentityMatrix();
    backgroundFrameAnchorMatrix = GlUtil.create4x4IdentityMatrix();
    overlayFrameAnchorMatrix = GlUtil.create4x4IdentityMatrix();
    rotateMatrix = GlUtil.create4x4IdentityMatrix();
    scaleMatrix = GlUtil.create4x4IdentityMatrix();
    scaleMatrixInv = GlUtil.create4x4IdentityMatrix();
    overlayAspectRatioMatrix = GlUtil.create4x4IdentityMatrix();
    overlayAspectRatioMatrixInv = GlUtil.create4x4IdentityMatrix();
    transformationMatrix = GlUtil.create4x4IdentityMatrix();
  }

  public void configure(Size backgroundSize) {
    this.backgroundSize = backgroundSize;
  }

  /**
   * Returns the transformation matrix.
   *
   * <p>This instance must be {@linkplain #configure configured} before this method is called.
   */
  public float[] getTransformationMatrix(Size overlaySize, OverlaySettings overlaySettings) {
    reset();

    // Anchor point of overlay within output frame.
    Pair<Float, Float> backgroundFrameAnchor = overlaySettings.backgroundFrameAnchor;
    Matrix.translateM(
        backgroundFrameAnchorMatrix,
        MATRIX_OFFSET,
        backgroundFrameAnchor.first,
        backgroundFrameAnchor.second,
        /* z= */ 0f);

    checkStateNotNull(backgroundSize);
    Matrix.scaleM(
        aspectRatioMatrix,
        MATRIX_OFFSET,
        (float) overlaySize.getWidth() / backgroundSize.getWidth(),
        (float) overlaySize.getHeight() / backgroundSize.getHeight(),
        /* z= */ 1f);

    // Scale the image.
    Pair<Float, Float> scale = overlaySettings.scale;
    Matrix.scaleM(scaleMatrix, MATRIX_OFFSET, scale.first, scale.second, /* z= */ 1f);
    Matrix.invertM(scaleMatrixInv, MATRIX_OFFSET, scaleMatrix, MATRIX_OFFSET);

    // Translate the overlay within its frame. To position the overlay frame's anchor at the correct
    // position, it must be translated the opposite direction by the same magnitude.
    Pair<Float, Float> overlayFrameAnchor = overlaySettings.overlayFrameAnchor;
    Matrix.translateM(
        overlayFrameAnchorMatrix,
        MATRIX_OFFSET,
        -1 * overlayFrameAnchor.first,
        -1 * overlayFrameAnchor.second,
        /* z= */ 0f);

    // Rotate the image.
    Matrix.rotateM(
        rotateMatrix,
        MATRIX_OFFSET,
        overlaySettings.rotationDegrees,
        /* x= */ 0f,
        /* y= */ 0f,
        /* z= */ 1f);

    // Rotation matrix needs to account for overlay aspect ratio to prevent stretching.
    Matrix.scaleM(
        overlayAspectRatioMatrix,
        MATRIX_OFFSET,
        (float) overlaySize.getHeight() / (float) overlaySize.getWidth(),
        /* y= */ 1f,
        /* z= */ 1f);
    Matrix.invertM(
        overlayAspectRatioMatrixInv, MATRIX_OFFSET, overlayAspectRatioMatrix, MATRIX_OFFSET);

    // transformationMatrix = backgroundFrameAnchorMatrix * aspectRatioMatrix
    //   * scaleMatrix * overlayFrameAnchorMatrix * scaleMatrixInv
    //   * overlayAspectRatioMatrix * rotateMatrix * overlayAspectRatioMatrixInv
    //   * scaleMatrix.

    // Anchor position in output frame.
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        backgroundFrameAnchorMatrix,
        MATRIX_OFFSET);

    // Correct for aspect ratio of image in output frame.
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        aspectRatioMatrix,
        MATRIX_OFFSET);

    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        scaleMatrix,
        MATRIX_OFFSET);
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        overlayFrameAnchorMatrix,
        MATRIX_OFFSET);
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        scaleMatrixInv,
        MATRIX_OFFSET);

    // Rotation needs to be agnostic of the scaling matrix and the aspect ratios.
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        overlayAspectRatioMatrix,
        MATRIX_OFFSET);
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

    // Scale image.
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        scaleMatrix,
        MATRIX_OFFSET);

    return transformationMatrix;
  }

  private void reset() {
    GlUtil.setToIdentity(aspectRatioMatrix);
    GlUtil.setToIdentity(backgroundFrameAnchorMatrix);
    GlUtil.setToIdentity(overlayFrameAnchorMatrix);
    GlUtil.setToIdentity(scaleMatrix);
    GlUtil.setToIdentity(scaleMatrixInv);
    GlUtil.setToIdentity(rotateMatrix);
    GlUtil.setToIdentity(overlayAspectRatioMatrix);
    GlUtil.setToIdentity(overlayAspectRatioMatrixInv);
    GlUtil.setToIdentity(transformationMatrix);
  }
}
