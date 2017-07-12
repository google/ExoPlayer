/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.test.MoreAsserts;

import junit.framework.TestCase;

/**
 * Tests for {@link ParsableBitArray}.
 */
public final class ParsableBitArrayTest extends TestCase {

  private static final byte[] TEST_DATA = new byte[] {0x3C, (byte) 0xD2, (byte) 0x5F, (byte) 0x01,
      (byte) 0xFF, (byte) 0x14, (byte) 0x60, (byte) 0x99};

  public void testReadAllBytes() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    byte[] bytesRead = new byte[TEST_DATA.length];
    testArray.readBytes(bytesRead, 0, TEST_DATA.length);
    MoreAsserts.assertEquals(TEST_DATA, bytesRead);
    assertEquals(TEST_DATA.length * 8, testArray.getPosition());
    assertEquals(TEST_DATA.length, testArray.getBytePosition());
  }

  public void testReadBit() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    assertReadBitsToEnd(0, testArray);
  }

  public void testReadBits() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    assertEquals(getTestDataBits(0, 5), testArray.readBits(5));
    assertEquals(getTestDataBits(5, 3), testArray.readBits(3));
    assertEquals(getTestDataBits(8, 16), testArray.readBits(16));
    assertEquals(getTestDataBits(24, 3), testArray.readBits(3));
    assertEquals(getTestDataBits(27, 18), testArray.readBits(18));
    assertEquals(getTestDataBits(45, 5), testArray.readBits(5));
    assertEquals(getTestDataBits(50, 14), testArray.readBits(14));
  }

  public void testRead32BitsByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    assertEquals(getTestDataBits(0, 32), testArray.readBits(32));
    assertEquals(getTestDataBits(32, 32), testArray.readBits(32));
  }

  public void testRead32BitsNonByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    assertEquals(getTestDataBits(0, 5), testArray.readBits(5));
    assertEquals(getTestDataBits(5, 32), testArray.readBits(32));
  }

  public void testSkipBytes() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.skipBytes(2);
    assertReadBitsToEnd(16, testArray);
  }

  public void testSkipBitsByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.skipBits(16);
    assertReadBitsToEnd(16, testArray);
  }

  public void testSkipBitsNonByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.skipBits(5);
    assertReadBitsToEnd(5, testArray);
  }

  public void testSetPositionByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.setPosition(16);
    assertReadBitsToEnd(16, testArray);
  }

  public void testSetPositionNonByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.setPosition(5);
    assertReadBitsToEnd(5, testArray);
  }

  public void testByteAlignFromNonByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.setPosition(11);
    testArray.byteAlign();
    assertEquals(2, testArray.getBytePosition());
    assertEquals(16, testArray.getPosition());
    assertReadBitsToEnd(16, testArray);
  }

  public void testByteAlignFromByteAligned() {
    ParsableBitArray testArray = new ParsableBitArray(TEST_DATA);
    testArray.setPosition(16);
    testArray.byteAlign(); // Should be a no-op.
    assertEquals(2, testArray.getBytePosition());
    assertEquals(16, testArray.getPosition());
    assertReadBitsToEnd(16, testArray);
  }

  private static void assertReadBitsToEnd(int expectedStartPosition, ParsableBitArray testArray) {
    int position = testArray.getPosition();
    assertEquals(expectedStartPosition, position);
    for (int i = position; i < TEST_DATA.length * 8; i++) {
      assertEquals(getTestDataBit(i), testArray.readBit());
      assertEquals(i + 1, testArray.getPosition());
    }
  }

  private static int getTestDataBits(int bitPosition, int length) {
    int result = 0;
    for (int i = 0; i < length; i++) {
      result = result << 1;
      if (getTestDataBit(bitPosition++)) {
        result |= 0x1;
      }
    }
    return result;
  }

  private static boolean getTestDataBit(int bitPosition) {
    return (TEST_DATA[bitPosition / 8] & (0x80 >>> (bitPosition % 8))) != 0;
  }

}
