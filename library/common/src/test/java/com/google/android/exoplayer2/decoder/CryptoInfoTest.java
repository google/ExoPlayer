/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.exoplayer2.decoder;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link CryptoInfo} */
@RunWith(AndroidJUnit4.class)
public class CryptoInfoTest {

  private CryptoInfo cryptoInfo;

  @Before
  public void setUp() {
    cryptoInfo = new CryptoInfo();
  }

  @Test
  public void increaseClearDataFirstSubSampleBy_numBytesOfClearDataIsNullAndZeroInput_isNoOp() {
    cryptoInfo.increaseClearDataFirstSubSampleBy(0);

    assertThat(cryptoInfo.numBytesOfClearData).isNull();
    assertThat(cryptoInfo.getFrameworkCryptoInfo().numBytesOfClearData).isNull();
  }

  @Test
  public void increaseClearDataFirstSubSampleBy_withNumBytesOfClearDataSetAndZeroInput_isNoOp() {
    int[] data = new int[] {1, 1, 1, 1};
    cryptoInfo.numBytesOfClearData = data;
    cryptoInfo.getFrameworkCryptoInfo().numBytesOfClearData = data;

    cryptoInfo.increaseClearDataFirstSubSampleBy(5);

    assertThat(cryptoInfo.numBytesOfClearData[0]).isEqualTo(6);
    assertThat(cryptoInfo.getFrameworkCryptoInfo().numBytesOfClearData[0]).isEqualTo(6);
  }

  @Test
  public void increaseClearDataFirstSubSampleBy_withSharedClearDataPointer_setsValue() {
    int[] data = new int[] {1, 1, 1, 1};
    cryptoInfo.numBytesOfClearData = data;
    cryptoInfo.getFrameworkCryptoInfo().numBytesOfClearData = data;

    cryptoInfo.increaseClearDataFirstSubSampleBy(5);

    assertThat(cryptoInfo.numBytesOfClearData[0]).isEqualTo(6);
    assertThat(cryptoInfo.getFrameworkCryptoInfo().numBytesOfClearData[0]).isEqualTo(6);
  }
}
