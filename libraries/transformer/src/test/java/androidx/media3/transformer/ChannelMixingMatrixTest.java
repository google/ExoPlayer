/*
 * Copyright 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link ChannelMixingMatrix}. */
@RunWith(AndroidJUnit4.class)
public class ChannelMixingMatrixTest {

  @Test
  public void onesOnDiagonal_1To1_hasCorrectProperties() {
    int inputCount = 1;
    int outputCount = 1;
    float[] coefficients = new float[] {1f};
    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isTrue();
    assertThat(matrix.isDiagonal()).isTrue();
    assertThat(matrix.isIdentity()).isTrue();
  }

  @Test
  public void onesOnDiagonal_2To3_hasCorrectProperties() {
    int inputCount = 2;
    int outputCount = 3;
    float[] coefficients =
        new float[] {
          1f, 0f, 0f,
          0f, 1f, 0f,
        };
    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isFalse();
    assertThat(matrix.isDiagonal()).isFalse();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void onesOnDiagonal_3To3_hasCorrectProperties() {
    int inputCount = 3;
    int outputCount = 3;
    float[] coefficients =
        new float[] {
          1f, 0f, 0f,
          0f, 1f, 0f,
          0f, 0f, 1f
        };
    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isTrue();
    assertThat(matrix.isDiagonal()).isTrue();
    assertThat(matrix.isIdentity()).isTrue();
  }

  @Test
  public void allZeroValues_3To2_hasCorrectProperties() {
    int inputCount = 3;
    int outputCount = 2;
    float[] coefficients =
        new float[] {
          0f, 0f,
          0f, 0f,
          0f, 0f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isTrue();
    assertThat(matrix.isSquare()).isFalse();
    assertThat(matrix.isDiagonal()).isFalse();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void allZeroValues_3To3_hasCorrectProperties() {
    int inputCount = 3;
    int outputCount = 3;
    float[] coefficients =
        new float[] {
          0f, 0f, 0f,
          0f, 0f, 0f,
          0f, 0f, 0f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isTrue();
    assertThat(matrix.isSquare()).isTrue();
    assertThat(matrix.isDiagonal()).isTrue();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void allZeroValues_3To4_hasCorrectProperties() {
    int inputCount = 3;
    int outputCount = 4;
    float[] coefficients =
        new float[] {
          0f, 0f, 0f, 0f,
          0f, 0f, 0f, 0f,
          0f, 0f, 0f, 0f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isTrue();
    assertThat(matrix.isSquare()).isFalse();
    assertThat(matrix.isDiagonal()).isFalse();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void oneNonZeroValue_3To4_hasCorrectProperties() {
    int inputCount = 3;
    int outputCount = 4;
    float[] coefficients =
        new float[] {
          0f, 0f, 0f, 0f,
          0f, 0f, 0f, 0.2f,
          0f, 0f, 0f, 0f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isFalse();
    assertThat(matrix.isDiagonal()).isFalse();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void zeroValuesOnDiagonal_2To2_hasCorrectProperties() {
    int inputCount = 2;
    int outputCount = 2;
    float[] coefficients =
        new float[] {
          0f, 1f,
          2f, 0f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isTrue();
    assertThat(matrix.isDiagonal()).isFalse();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void nonZeroValuesOnDiagonal_4To4_hasCorrectProperties() {
    int inputCount = 4;
    int outputCount = 4;
    float[] coefficients =
        new float[] {
          1f, 0f, 0f, 0f,
          0f, 2f, 0f, 0f,
          0f, 0f, 3f, 0f,
          0f, 0f, 0f, 0f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isTrue();
    assertThat(matrix.isDiagonal()).isTrue();
    assertThat(matrix.isIdentity()).isFalse();
  }

  @Test
  public void allNonZeroValues_2To4_hasCorrectProperties() {
    int inputCount = 2;
    int outputCount = 4;
    float[] coefficients =
        new float[] {
          1f, 3f, 5f, 10f,
          4f, 2f, 9f, 123f,
        };

    ChannelMixingMatrix matrix = new ChannelMixingMatrix(inputCount, outputCount, coefficients);
    assertThat(matrix.isZero()).isFalse();
    assertThat(matrix.isSquare()).isFalse();
    assertThat(matrix.isDiagonal()).isFalse();
    assertThat(matrix.isIdentity()).isFalse();
  }
}
