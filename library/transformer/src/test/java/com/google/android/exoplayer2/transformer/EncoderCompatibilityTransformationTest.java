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
package com.google.android.exoplayer2.transformer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link EncoderCompatibilityTransformation}. */
@RunWith(AndroidJUnit4.class)
public final class EncoderCompatibilityTransformationTest {
  @Test
  public void configure_noEditsLandscape_leavesOrientationUnchanged() {
    int inputWidth = 200;
    int inputHeight = 150;
    EncoderCompatibilityTransformation encoderCompatibilityTransformation =
        new EncoderCompatibilityTransformation();

    Size outputSize = encoderCompatibilityTransformation.configure(inputWidth, inputHeight);

    assertThat(encoderCompatibilityTransformation.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void configure_noEditsSquare_leavesOrientationUnchanged() {
    int inputWidth = 150;
    int inputHeight = 150;
    EncoderCompatibilityTransformation encoderCompatibilityTransformation =
        new EncoderCompatibilityTransformation();

    Size outputSize = encoderCompatibilityTransformation.configure(inputWidth, inputHeight);

    assertThat(encoderCompatibilityTransformation.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void configure_noEditsPortrait_flipsOrientation() {
    int inputWidth = 150;
    int inputHeight = 200;
    EncoderCompatibilityTransformation encoderCompatibilityTransformation =
        new EncoderCompatibilityTransformation();

    Size outputSize = encoderCompatibilityTransformation.configure(inputWidth, inputHeight);

    assertThat(encoderCompatibilityTransformation.getOutputRotationDegrees()).isEqualTo(90);
    assertThat(outputSize.getWidth()).isEqualTo(inputHeight);
    assertThat(outputSize.getHeight()).isEqualTo(inputWidth);
  }

  @Test
  public void getOutputRotationDegreesBeforeConfigure_throwsIllegalStateException() {
    EncoderCompatibilityTransformation encoderCompatibilityTransformation =
        new EncoderCompatibilityTransformation();

    // configure not called before getOutputRotationDegrees.
    assertThrows(
        IllegalStateException.class, encoderCompatibilityTransformation::getOutputRotationDegrees);
  }
}
