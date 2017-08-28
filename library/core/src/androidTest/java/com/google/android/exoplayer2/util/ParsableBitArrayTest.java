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

  private ParsableBitArray testArray;

  @Override
  public void setUp() {
    testArray = new ParsableBitArray(TEST_DATA);
  }

  public void testReadAllBytes() {
    byte[] bytesRead = new byte[TEST_DATA.length];
    testArray.readBytes(bytesRead, 0, TEST_DATA.length);
    MoreAsserts.assertEquals(TEST_DATA, bytesRead);
    assertEquals(TEST_DATA.length * 8, testArray.getPosition());
    assertEquals(TEST_DATA.length, testArray.getBytePosition());
  }

  public void testReadBit() {
    assertReadBitsToEnd(0);
  }

  public void testReadBits() {
    assertEquals(getTestDataBits(0, 5), testArray.readBits(5));
    assertEquals(getTestDataBits(5, 0), testArray.readBits(0));
    assertEquals(getTestDataBits(5, 3), testArray.readBits(3));
    assertEquals(getTestDataBits(8, 16), testArray.readBits(16));
    assertEquals(getTestDataBits(24, 3), testArray.readBits(3));
    assertEquals(getTestDataBits(27, 18), testArray.readBits(18));
    assertEquals(getTestDataBits(45, 5), testArray.readBits(5));
    assertEquals(getTestDataBits(50, 14), testArray.readBits(14));
  }

  public void testReadBitsToByteArray() {
    byte[] result = new byte[TEST_DATA.length];
    // Test read within byte boundaries.
    testArray.readBits(result, 0, 6);
    assertEquals(TEST_DATA[0] & 0xFC, result[0]);
    // Test read across byte boundaries.
    testArray.readBits(result, 0, 8);
    assertEquals(((TEST_DATA[0] & 0x03) << 6) | ((TEST_DATA[1] & 0xFC) >> 2), result[0]);
    // Test reading across multiple bytes.
    testArray.readBits(result, 1, 50);
    for (int i = 1; i < 7; i++) {
      assertEquals((byte) (((TEST_DATA[i] & 0x03) << 6) | ((TEST_DATA[i + 1] & 0xFC) >> 2)),
          result[i]);
    }
    assertEquals((byte) (TEST_DATA[7] & 0x03) << 6, result[7]);
    assertEquals(0, testArray.bitsLeft());
    // Test read last buffer byte across input data bytes.
    testArray.setPosition(31);
    result[3] = 0;
    testArray.readBits(result, 3, 3);
    assertEquals((byte) 0xE0, result[3]);
    // Test read bits in the middle of a input data byte.
    result[0] = 0;
    assertEquals(34, testArray.getPosition());
    testArray.readBits(result, 0, 3);
    assertEquals((byte) 0xE0, result[0]);
    // Test read 0 bits.
    testArray.setPosition(32);
    result[1] = 0;
    testArray.readBits(result, 1, 0);
    assertEquals(0, result[1]);
    // Test reading a number of bits divisible by 8.
    testArray.setPosition(0);
    testArray.readBits(result, 0, 16);
    assertEquals(TEST_DATA[0], result[0]);
    assertEquals(TEST_DATA[1], result[1]);
    // Test least significant bits are unmodified.
    result[1] = (byte) 0xFF;
    testArray.readBits(result, 0, 9);
    assertEquals(0x5F, result[0]);
    assertEquals(0x7F, result[1]);
  }

  public void testRead32BitsByteAligned() {
    assertEquals(getTestDataBits(0, 32), testArray.readBits(32));
    assertEquals(getTestDataBits(32, 32), testArray.readBits(32));
  }

  public void testRead32BitsNonByteAligned() {
    assertEquals(getTestDataBits(0, 5), testArray.readBits(5));
    assertEquals(getTestDataBits(5, 32), testArray.readBits(32));
  }

  public void testSkipBytes() {
    testArray.skipBytes(2);
    assertReadBitsToEnd(16);
  }

  public void testSkipBitsByteAligned() {
    testArray.skipBits(16);
    assertReadBitsToEnd(16);
  }

  public void testSkipBitsNonByteAligned() {
    testArray.skipBits(5);
    assertReadBitsToEnd(5);
  }

  public void testSetPositionByteAligned() {
    testArray.setPosition(16);
    assertReadBitsToEnd(16);
  }

  public void testSetPositionNonByteAligned() {
    testArray.setPosition(5);
    assertReadBitsToEnd(5);
  }

  public void testByteAlignFromNonByteAligned() {
    testArray.setPosition(11);
    testArray.byteAlign();
    assertEquals(2, testArray.getBytePosition());
    assertEquals(16, testArray.getPosition());
    assertReadBitsToEnd(16);
  }

  public void testByteAlignFromByteAligned() {
    testArray.setPosition(16);
    testArray.byteAlign(); // Should be a no-op.
    assertEquals(2, testArray.getBytePosition());
    assertEquals(16, testArray.getPosition());
    assertReadBitsToEnd(16);
  }

  private void assertReadBitsToEnd(int expectedStartPosition) {
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
