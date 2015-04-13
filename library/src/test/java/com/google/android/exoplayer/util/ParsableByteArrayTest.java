/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.util;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Tests for {@link ParsableByteArray}.
 */
public class ParsableByteArrayTest extends TestCase {

  private static final byte[] ARRAY_ELEMENTS =
      new byte[] {0x0F, (byte) 0xFF, (byte) 0x42, (byte) 0x0F, 0x00, 0x00, 0x00, 0x00};

  private ParsableByteArray parsableByteArray;

  @Override
  public void setUp() {
    parsableByteArray = new ParsableByteArray(ARRAY_ELEMENTS.length);
    System.arraycopy(ARRAY_ELEMENTS, 0, parsableByteArray.data, 0, ARRAY_ELEMENTS.length);
  }

  public void testReadInt() {
    // When reading a signed integer
    int value = parsableByteArray.readInt();

    // Then the read value is equal to the array elements interpreted as an int.
    assertEquals((0xFF & ARRAY_ELEMENTS[0]) << 24 | (0xFF & ARRAY_ELEMENTS[1]) << 16
        | (0xFF & ARRAY_ELEMENTS[2]) << 8 | (0xFF & ARRAY_ELEMENTS[3]), value);
  }

  public void testSkipBack() {
    // When reading an unsigned integer
    long value = parsableByteArray.readUnsignedInt();

    // Then skipping back and reading gives the same value.
    parsableByteArray.skip(-4);
    assertEquals(value, parsableByteArray.readUnsignedInt());
  }

  public void testReadingMovesPosition() {
    // Given an array at the start
    assertEquals(0, parsableByteArray.getPosition());

    // When reading an integer, the position advances
    parsableByteArray.readUnsignedInt();
    assertEquals(4, parsableByteArray.getPosition());
  }

  public void testOutOfBoundsThrows() {
    // Given an array at the end
    parsableByteArray.readUnsignedLongToLong();
    assertEquals(ARRAY_ELEMENTS.length, parsableByteArray.getPosition());

    // Then reading more data throws.
    try {
      parsableByteArray.readUnsignedInt();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  public void testModificationsAffectParsableArray() {
    // When modifying the wrapped byte array
    byte[] data = parsableByteArray.data;
    long readValue = parsableByteArray.readUnsignedInt();
    data[0] = (byte) (ARRAY_ELEMENTS[0] + 1);
    parsableByteArray.setPosition(0);

    // Then the parsed value changes.
    assertFalse(parsableByteArray.readUnsignedInt() == readValue);
  }

  public void testReadingUnsignedLongWithMsbSetThrows() {
    // Given an array with the most-significant bit set on the top byte
    byte[] data = parsableByteArray.data;
    data[0] = (byte) 0x80;

    // Then reading an unsigned long throws.
    try {
      parsableByteArray.readUnsignedLongToLong();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  public void testReadUnsignedFixedPoint1616() {
    // When reading the integer part of a 16.16 fixed point value
    int value = parsableByteArray.readUnsignedFixedPoint1616();

    // Then the read value is equal to the array elements interpreted as a short.
    assertEquals((0xFF & ARRAY_ELEMENTS[0]) << 8 | (ARRAY_ELEMENTS[1] & 0xFF), value);
    assertEquals(4, parsableByteArray.getPosition());
  }

  public void testReadingBytesReturnsCopy() {
    // When reading all the bytes back
    int length = parsableByteArray.limit();
    assertEquals(ARRAY_ELEMENTS.length, length);
    byte[] copy = new byte[length];
    parsableByteArray.readBytes(copy, 0, length);

    // Then the array elements are the same.
    assertTrue(Arrays.equals(parsableByteArray.data, copy));
  }

}
