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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.opengl.Matrix;
import android.util.Pair;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Size;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

@Deprecated
/* package */ final class OverlayMatrixProvider {
  private static final int MATRIX_OFFSET = 0;
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
  private @MonotonicNonNull Size backgroundSize;

  public OverlayMatrixProvider() {
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
  }

  public void configure(Size backgroundSize) {
    this.backgroundSize = backgroundSize;
  }

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
    Matrix.invertM(videoFrameAnchorMatrixInv, MATRIX_OFFSET, videoFrameAnchorMatrix, MATRIX_OFFSET);

    Matrix.scaleM(
        aspectRatioMatrix,
        MATRIX_OFFSET,
        checkNotNull(backgroundSize).getWidth() / (float) overlaySize.getWidth(),
        checkNotNull(backgroundSize).getHeight() / (float) overlaySize.getHeight(),
        /* z= */ 1f);

    // Scale the image.
    Pair<Float, Float> scale = overlaySettings.scale;
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
    Pair<Float, Float> overlayAnchor = overlaySettings.overlayAnchor;
    Matrix.translateM(
        overlayAnchorMatrix, MATRIX_OFFSET, overlayAnchor.first, overlayAnchor.second, /* z= */ 0f);
    Matrix.invertM(overlayAnchorMatrixInv, MATRIX_OFFSET, overlayAnchorMatrix, MATRIX_OFFSET);

    // Rotate the image.
    Matrix.rotateM(
        rotateMatrix,
        MATRIX_OFFSET,
        rotateMatrix,
        MATRIX_OFFSET,
        overlaySettings.rotationDegrees,
        /* x= */ 0f,
        /* y= */ 0f,
        /* z= */ 1f);
    Matrix.invertM(rotateMatrix, MATRIX_OFFSET, rotateMatrix, MATRIX_OFFSET);

    // Rotation matrix needs to account for overlay aspect ratio to prevent stretching.
    Matrix.scaleM(
        overlayAspectRatioMatrix,
        MATRIX_OFFSET,
        (float) overlaySize.getHeight() / (float) overlaySize.getWidth(),
        /* y= */ 1f,
        /* z= */ 1f);
    Matrix.invertM(
        overlayAspectRatioMatrixInv, MATRIX_OFFSET, overlayAspectRatioMatrix, MATRIX_OFFSET);

    // Rotation needs to be agnostic of the scaling matrix and the aspect ratios.
    // transformationMatrix = scaleMatrixInv * overlayAspectRatioMatrix * rotateMatrix *
    //   overlayAspectRatioInv * scaleMatrix * overlayAnchorMatrixInv * scaleMatrixInv *
    //   aspectRatioMatrix * videoFrameAnchorMatrixInv
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
    return transformationMatrix;
  }

  private void reset() {
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
  }
}
