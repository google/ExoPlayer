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
package androidx.media3.effect;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link GaussianFunction}. */
@RunWith(AndroidJUnit4.class)
public class GaussianFunctionTest {

  private final GaussianFunction function =
      new GaussianFunction(/* sigma= */ 2.55f, /* numStandardDeviations= */ 4.45f);

  @Test
  public void value_samplePositionAboveRange_returnsZero() {
    assertThat(function.value(/* samplePosition= */ function.domainEnd() + .00001f)).isEqualTo(0);
  }

  @Test
  public void value_samplePositionBelowRange_returnsZero() {
    assertThat(function.value(/* samplePosition= */ -10000000000000f)).isEqualTo(0);
  }

  @Test
  public void value_samplePositionInRange_returnsSymmetricGaussianFunction() {
    assertThat(function.value(/* samplePosition= */ 9.999f)).isEqualTo(7.1712595E-5f);
    assertThat(function.value(/* samplePosition= */ -9.999f)).isEqualTo(7.1712595E-5f);
  }
}
