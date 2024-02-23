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
package androidx.media3.demo.transformer;

import android.graphics.Matrix;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.effect.GlMatrixTransformation;
import androidx.media3.effect.MatrixTransformation;

/**
 * Factory for {@link GlMatrixTransformation GlMatrixTransformations} and {@link
 * MatrixTransformation MatrixTransformations} that create video effects by applying transformation
 * matrices to the individual video frames.
 */
/* package */ final class MatrixTransformationFactory {
  /**
   * Returns a {@link MatrixTransformation} that rescales the frames over the first {@link
   * #ZOOM_DURATION_SECONDS} seconds, such that the rectangle filled with the input frame increases
   * linearly in size from a single point to filling the full output frame.
   */
  public static MatrixTransformation createZoomInTransition() {
    return MatrixTransformationFactory::calculateZoomInTransitionMatrix;
  }

  /**
   * Returns a {@link MatrixTransformation} that crops frames to a rectangle that moves on an
   * ellipse.
   */
  public static MatrixTransformation createDizzyCropEffect() {
    return MatrixTransformationFactory::calculateDizzyCropMatrix;
  }

  /**
   * Returns a {@link GlMatrixTransformation} that rotates a frame in 3D around the y-axis and
   * applies perspective projection to 2D.
   */
  public static GlMatrixTransformation createSpin3dEffect() {
    return MatrixTransformationFactory::calculate3dSpinMatrix;
  }

  private static final float ZOOM_DURATION_SECONDS = 2f;
  private static final float DIZZY_CROP_ROTATION_PERIOD_US = 5_000_000f;

  private static Matrix calculateZoomInTransitionMatrix(long presentationTimeUs) {
    Matrix transformationMatrix = new Matrix();
    float scale = Math.min(1, presentationTimeUs / (C.MICROS_PER_SECOND * ZOOM_DURATION_SECONDS));
    transformationMatrix.postScale(/* sx= */ scale, /* sy= */ scale);
    return transformationMatrix;
  }

  private static android.graphics.Matrix calculateDizzyCropMatrix(long presentationTimeUs) {
    double theta = presentationTimeUs * 2 * Math.PI / DIZZY_CROP_ROTATION_PERIOD_US;
    float centerX = 0.5f * (float) Math.cos(theta);
    float centerY = 0.5f * (float) Math.sin(theta);
    android.graphics.Matrix transformationMatrix = new android.graphics.Matrix();
    transformationMatrix.postTranslate(/* dx= */ centerX, /* dy= */ centerY);
    transformationMatrix.postScale(/* sx= */ 2f, /* sy= */ 2f);
    return transformationMatrix;
  }

  private static float[] calculate3dSpinMatrix(long presentationTimeUs) {
    float[] transformationMatrix = new float[16];
    android.opengl.Matrix.frustumM(
        transformationMatrix,
        /* offset= */ 0,
        /* left= */ -1f,
        /* right= */ 1f,
        /* bottom= */ -1f,
        /* top= */ 1f,
        /* near= */ 3f,
        /* far= */ 5f);
    android.opengl.Matrix.translateM(
        transformationMatrix, /* mOffset= */ 0, /* x= */ 0f, /* y= */ 0f, /* z= */ -4f);
    float theta = Util.usToMs(presentationTimeUs) / 10f;
    android.opengl.Matrix.rotateM(
        transformationMatrix, /* mOffset= */ 0, theta, /* x= */ 0f, /* y= */ 1f, /* z= */ 0f);
    return transformationMatrix;
  }
}
