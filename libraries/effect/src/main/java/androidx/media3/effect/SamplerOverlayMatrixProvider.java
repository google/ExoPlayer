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

import android.opengl.Matrix;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;

/**
 * Provides a matrix based on {@link OverlaySettings} to be applied on a texture sampling
 * coordinate.
 */
/* package */ final class SamplerOverlayMatrixProvider extends OverlayMatrixProvider {
  private final float[] transformationMatrixInv;

  public SamplerOverlayMatrixProvider() {
    super();
    transformationMatrixInv = GlUtil.create4x4IdentityMatrix();
  }

  @Override
  public float[] getTransformationMatrix(Size overlaySize, OverlaySettings overlaySettings) {
    // When sampling from a (for example, texture) sampler, the overlay anchor's x and y coordinates
    // are flipped.
    OverlaySettings samplerOverlaySettings =
        overlaySettings
            .buildUpon()
            .setOverlayFrameAnchor(
                /* x= */ -1 * overlaySettings.overlayFrameAnchor.first,
                /* y= */ -1 * overlaySettings.overlayFrameAnchor.second)
            .build();

    // When sampling from a (for example, texture) sampler, the transformation matrix applied to a
    // sampler's coordinate should be the inverse of the transformation matrix that would otherwise
    // be applied to a vertex.
    Matrix.invertM(
        transformationMatrixInv,
        MATRIX_OFFSET,
        super.getTransformationMatrix(overlaySize, samplerOverlaySettings),
        MATRIX_OFFSET);
    return transformationMatrixInv;
  }
}
