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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.opengl.Matrix;
import android.util.Pair;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Size;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Provides a matrix for {@link OverlaySettings}, to be applied on a vertex.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ class OverlayMatrixProvider {
  protected static final int MATRIX_OFFSET = 0;
  private final float[] videoFrameAnchorMatrix;
  private final float[] aspectRatioMatrix;
  private final float[] scaleMatrix;
  private final float[] scaleMatrixInv;
  private final float[] overlayAnchorMatrix;
  private final float[] rotateMatrix;
  private final float[] overlayAspectRatioMatrix;
  private final float[] overlayAspectRatioMatrixInv;
  private final float[] transformationMatrix;
  private @MonotonicNonNull Size backgroundSize;

  public OverlayMatrixProvider() {
    aspectRatioMatrix = GlUtil.create4x4IdentityMatrix();
    videoFrameAnchorMatrix = GlUtil.create4x4IdentityMatrix();
    overlayAnchorMatrix = GlUtil.create4x4IdentityMatrix();
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
    Pair<Float, Float> videoFrameAnchor = overlaySettings.videoFrameAnchor;
    Matrix.translateM(
        videoFrameAnchorMatrix,
        MATRIX_OFFSET,
        videoFrameAnchor.first,
        videoFrameAnchor.second,
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

    // Translate the overlay within its frame. To position the overlay's anchor at the correct
    // position, it must be translated the opposite direction by the same magnitude.
    Pair<Float, Float> overlayAnchor = overlaySettings.overlayAnchor;
    Matrix.translateM(
        overlayAnchorMatrix,
        MATRIX_OFFSET,
        -1 * overlayAnchor.first,
        -1 * overlayAnchor.second,
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

    // transformationMatrix = videoFrameAnchorMatrix * aspectRatioMatrix
    //   * scaleMatrix * overlayAnchorMatrix * scaleMatrixInv
    //   * overlayAspectRatioMatrix * rotateMatrix * overlayAspectRatioMatrixInv
    //   * scaleMatrix.

    // Anchor position in output frame.
    Matrix.multiplyMM(
        transformationMatrix,
        MATRIX_OFFSET,
        transformationMatrix,
        MATRIX_OFFSET,
        videoFrameAnchorMatrix,
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
        overlayAnchorMatrix,
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
    GlUtil.setToIdentity(videoFrameAnchorMatrix);
    GlUtil.setToIdentity(overlayAnchorMatrix);
    GlUtil.setToIdentity(scaleMatrix);
    GlUtil.setToIdentity(scaleMatrixInv);
    GlUtil.setToIdentity(rotateMatrix);
    GlUtil.setToIdentity(overlayAspectRatioMatrix);
    GlUtil.setToIdentity(overlayAspectRatioMatrixInv);
    GlUtil.setToIdentity(transformationMatrix);
  }
}
