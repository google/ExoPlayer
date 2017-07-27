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

import static com.google.android.exoplayer2.testutil.TestUtil.createByteArray;

import junit.framework.TestCase;

/**
 * Tests for {@link ParsableNalUnitBitArray}.
 */
public final class ParsableNalUnitBitArrayTest extends TestCase {

  private static final byte[] NO_ESCAPING_TEST_DATA = createByteArray(0, 3, 0, 1, 3, 0, 0);
  private static final byte[] ALL_ESCAPING_TEST_DATA = createByteArray(0, 0, 3, 0, 0, 3, 0, 0, 3);
  private static final byte[] MIX_TEST_DATA = createByteArray(255, 0, 0, 3, 255, 0, 0, 127);

  public void testReadNoEscaping() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(NO_ESCAPING_TEST_DATA, 0, NO_ESCAPING_TEST_DATA.length);
    assertEquals(0x000300, array.readBits(24));
    assertEquals(0, array.readBits(7));
    assertTrue(array.readBit());
    assertEquals(0x030000, array.readBits(24));
    assertFalse(array.canReadBits(1));
    assertFalse(array.canReadBits(8));
  }

  public void testReadNoEscapingTruncated() {
    ParsableNalUnitBitArray array = new ParsableNalUnitBitArray(NO_ESCAPING_TEST_DATA, 0, 4);
    assertTrue(array.canReadBits(32));
    array.skipBits(32);
    assertFalse(array.canReadBits(1));
    try {
      array.readBit();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  public void testReadAllEscaping() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(ALL_ESCAPING_TEST_DATA, 0, ALL_ESCAPING_TEST_DATA.length);
    assertTrue(array.canReadBits(48));
    assertFalse(array.canReadBits(49));
    assertEquals(0, array.readBits(15));
    assertFalse(array.readBit());
    assertEquals(0, array.readBits(17));
    assertEquals(0, array.readBits(15));
  }

  public void testReadMix() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(MIX_TEST_DATA, 0, MIX_TEST_DATA.length);
    assertTrue(array.canReadBits(56));
    assertFalse(array.canReadBits(57));
    assertEquals(127, array.readBits(7));
    assertEquals(2, array.readBits(2));
    assertEquals(3, array.readBits(17));
    assertEquals(126, array.readBits(7));
    assertEquals(127, array.readBits(23));
    assertFalse(array.canReadBits(1));
  }

  public void testReadExpGolomb() {
    ParsableNalUnitBitArray array = new ParsableNalUnitBitArray(createByteArray(0x9E), 0, 1);
    assertTrue(array.canReadExpGolombCodedNum());
    assertEquals(0, array.readUnsignedExpGolombCodedInt());
    assertEquals(6, array.readUnsignedExpGolombCodedInt());
    assertEquals(0, array.readUnsignedExpGolombCodedInt());
    assertFalse(array.canReadExpGolombCodedNum());
    try {
      array.readUnsignedExpGolombCodedInt();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  public void testReadExpGolombWithEscaping() {
    ParsableNalUnitBitArray array =
        new ParsableNalUnitBitArray(createByteArray(0, 0, 3, 128, 0), 0, 5);
    assertFalse(array.canReadExpGolombCodedNum());
    array.skipBit();
    assertTrue(array.canReadExpGolombCodedNum());
    assertEquals(32767, array.readUnsignedExpGolombCodedInt());
    assertFalse(array.canReadBits(1));
  }

  public void testReset() {
    ParsableNalUnitBitArray array = new ParsableNalUnitBitArray(createByteArray(0, 0), 0, 2);
    assertFalse(array.canReadExpGolombCodedNum());
    assertTrue(array.canReadBits(16));
    assertFalse(array.canReadBits(17));
    array.reset(createByteArray(0, 0, 3, 0), 0, 4);
    assertTrue(array.canReadBits(24));
    assertFalse(array.canReadBits(25));
  }

}
