/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ParsableNalUnitBitArray}. */
@RunWith(AndroidJUnit4.class)
public final class ParsableNalUnitBitArrayTest {

  private static final byte[] NO_ESCAPING_TEST_DATA = createByteArray(0, 3, 0, 1, 3, 0, 0);
  private static final byte[] ALL_ESCAPING_TEST_DATA = createByteArray(0, 0, 3, 0, 0, 3, 0, 0, 3);
  private static final byte[] MIX_TEST_DATA = createByteArray(255, 0, 0, 3, 255, 0, 0, 127);

  @Test
  public void readNoEscaping() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(NO_ESCAPING_TEST_DATA, 0, NO_ESCAPING_TEST_DATA.length);
    assertThat(array.readBits(24)).isEqualTo(0x000300);
    assertThat(array.readBits(7)).isEqualTo(0);
    assertThat(array.readBit()).isTrue();
    assertThat(array.readBits(24)).isEqualTo(0x030000);
    assertThat(array.canReadBits(1)).isFalse();
    assertThat(array.canReadBits(8)).isFalse();
  }

  @Test
  public void readNoEscapingTruncated() {
    ParsableNalUnitBitArray array = new ParsableNalUnitBitArray(NO_ESCAPING_TEST_DATA, 0, 4);
    assertThat(array.canReadBits(32)).isTrue();
    array.skipBits(32);
    assertThat(array.canReadBits(1)).isFalse();
    try {
      array.readBit();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void readAllEscaping() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(ALL_ESCAPING_TEST_DATA, 0, ALL_ESCAPING_TEST_DATA.length);
    assertThat(array.canReadBits(48)).isTrue();
    assertThat(array.canReadBits(49)).isFalse();
    assertThat(array.readBits(15)).isEqualTo(0);
    assertThat(array.readBit()).isFalse();
    assertThat(array.readBits(17)).isEqualTo(0);
    assertThat(array.readBits(15)).isEqualTo(0);
  }

  @Test
  public void readMix() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(MIX_TEST_DATA, 0, MIX_TEST_DATA.length);
    assertThat(array.canReadBits(56)).isTrue();
    assertThat(array.canReadBits(57)).isFalse();
    assertThat(array.readBits(7)).isEqualTo(127);
    assertThat(array.readBits(2)).isEqualTo(2);
    assertThat(array.readBits(17)).isEqualTo(3);
    assertThat(array.readBits(7)).isEqualTo(126);
    assertThat(array.readBits(23)).isEqualTo(127);
    assertThat(array.canReadBits(1)).isFalse();
  }

  @Test
  public void readExpGolomb() {
    ParsableNalUnitBitArray array = new ParsableNalUnitBitArray(createByteArray(0x9E), 0, 1);
    assertThat(array.canReadExpGolombCodedNum()).isTrue();
    assertThat(array.readUnsignedExpGolombCodedInt()).isEqualTo(0);
    assertThat(array.readUnsignedExpGolombCodedInt()).isEqualTo(6);
    assertThat(array.readUnsignedExpGolombCodedInt()).isEqualTo(0);
    assertThat(array.canReadExpGolombCodedNum()).isFalse();
    try {
      array.readUnsignedExpGolombCodedInt();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void readExpGolombWithEscaping() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(createByteArray(0, 0, 3, 128, 0), 0, 5);
    assertThat(array.canReadExpGolombCodedNum()).isFalse();
    array.skipBit();
    assertThat(array.canReadExpGolombCodedNum()).isTrue();
    assertThat(array.readUnsignedExpGolombCodedInt()).isEqualTo(32767);
    assertThat(array.canReadBits(1)).isFalse();
  }

  @Test
  public void reset() {
    ParsableNalUnitBitArray array = new ParsableNalUnitBitArray(createByteArray(0, 0), 0, 2);
    assertThat(array.canReadExpGolombCodedNum()).isFalse();
    assertThat(array.canReadBits(16)).isTrue();
    assertThat(array.canReadBits(17)).isFalse();
    array.reset(createByteArray(0, 0, 3, 0), 0, 4);
    assertThat(array.canReadBits(24)).isTrue();
    assertThat(array.canReadBits(25)).isFalse();
  }

  /** Converts an array of integers in the range [0, 255] into an equivalent byte array. */
  // TODO(internal b/161804035): Use TestUtils when it's available in a dependency we can use here.
  private static byte[] createByteArray(int... bytes) {
    byte[] byteArray = new byte[bytes.length];
    for (int i = 0; i < byteArray.length; i++) {
      Assertions.checkState(0x00 <= bytes[i] && bytes[i] <= 0xFF);
      byteArray[i] = (byte) bytes[i];
    }
    return byteArray;
  }
}
