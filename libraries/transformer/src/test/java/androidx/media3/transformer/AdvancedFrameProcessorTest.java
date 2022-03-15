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
package androidx.media3.transformer;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Matrix;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link AdvancedFrameProcessor}.
 *
 * <p>See {@link AdvancedFrameProcessorPixelTest} for pixel tests testing {@link
 * AdvancedFrameProcessor} given a transformation matrix.
 */
@RunWith(AndroidJUnit4.class)
public final class AdvancedFrameProcessorTest {
  @Test
  public void getOutputDimensions_withIdentityMatrix_leavesDimensionsUnchanged() {
    Matrix identityMatrix = new Matrix();
    int inputWidth = 200;
    int inputHeight = 150;
    AdvancedFrameProcessor advancedFrameProcessor =
        new AdvancedFrameProcessor(getApplicationContext(), identityMatrix);

    Pair<Integer, Integer> outputDimensions =
        advancedFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(outputDimensions.first).isEqualTo(inputWidth);
    assertThat(outputDimensions.second).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputDimensions_withTransformationMatrix_leavesDimensionsUnchanged() {
    Matrix transformationMatrix = new Matrix();
    transformationMatrix.postRotate(/* degrees= */ 90);
    transformationMatrix.postScale(/* sx= */ .5f, /* sy= */ 1.2f);
    int inputWidth = 200;
    int inputHeight = 150;
    AdvancedFrameProcessor advancedFrameProcessor =
        new AdvancedFrameProcessor(getApplicationContext(), transformationMatrix);

    Pair<Integer, Integer> outputDimensions =
        advancedFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(outputDimensions.first).isEqualTo(inputWidth);
    assertThat(outputDimensions.second).isEqualTo(inputHeight);
  }
}
