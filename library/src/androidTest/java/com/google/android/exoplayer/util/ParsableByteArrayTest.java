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

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Tests for {@link ParsableByteArray}.
 */
public class ParsableByteArrayTest extends TestCase {

  private static final byte[] TEST_DATA =
      new byte[] {0x0F, (byte) 0xFF, (byte) 0x42, (byte) 0x0F, 0x00, 0x00, 0x00, 0x00};

  private static ParsableByteArray getTestDataArray() {
    ParsableByteArray testArray = new ParsableByteArray(TEST_DATA.length);
    System.arraycopy(TEST_DATA, 0, testArray.data, 0, TEST_DATA.length);
    return testArray;
  }

  public void testReadInt() {
    testReadInt(0);
    testReadInt(1);
    testReadInt(-1);
    testReadInt(Integer.MIN_VALUE);
    testReadInt(Integer.MAX_VALUE);
  }

  private static void testReadInt(int testValue) {
    ParsableByteArray testArray = new ParsableByteArray(
        ByteBuffer.allocate(4).putInt(testValue).array());
    int readValue = testArray.readInt();

    // Assert that the value we read was the value we wrote.
    assertEquals(testValue, readValue);
    // And that the position advanced as expected.
    assertEquals(4, testArray.getPosition());

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-4);
    readValue = testArray.readInt();
    assertEquals(testValue, readValue);
    assertEquals(4, testArray.getPosition());
  }

  public void testReadUnsignedInt() {
    testReadUnsignedInt(0);
    testReadUnsignedInt(1);
    testReadUnsignedInt(Integer.MAX_VALUE);
    testReadUnsignedInt(Integer.MAX_VALUE + 1L);
    testReadUnsignedInt(0xFFFFFFFFL);
  }

  private static void testReadUnsignedInt(long testValue) {
    ParsableByteArray testArray = new ParsableByteArray(
        Arrays.copyOfRange(ByteBuffer.allocate(8).putLong(testValue).array(), 4, 8));
    long readValue = testArray.readUnsignedInt();

    // Assert that the value we read was the value we wrote.
    assertEquals(testValue, readValue);
    // And that the position advanced as expected.
    assertEquals(4, testArray.getPosition());

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-4);
    readValue = testArray.readUnsignedInt();
    assertEquals(testValue, readValue);
    assertEquals(4, testArray.getPosition());
  }

  public void testReadUnsignedIntToInt() {
    testReadUnsignedIntToInt(0);
    testReadUnsignedIntToInt(1);
    testReadUnsignedIntToInt(Integer.MAX_VALUE);
    try {
      testReadUnsignedIntToInt(-1);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
    try {
      testReadUnsignedIntToInt(Integer.MIN_VALUE);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  private static void testReadUnsignedIntToInt(int testValue) {
    ParsableByteArray testArray = new ParsableByteArray(
        ByteBuffer.allocate(4).putInt(testValue).array());
    int readValue = testArray.readUnsignedIntToInt();

    // Assert that the value we read was the value we wrote.
    assertEquals(testValue, readValue);
    // And that the position advanced as expected.
    assertEquals(4, testArray.getPosition());

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-4);
    readValue = testArray.readUnsignedIntToInt();
    assertEquals(testValue, readValue);
    assertEquals(4, testArray.getPosition());
  }

  public void testReadUnsignedLongToLong() {
    testReadUnsignedLongToLong(0);
    testReadUnsignedLongToLong(1);
    testReadUnsignedLongToLong(Long.MAX_VALUE);
    try {
      testReadUnsignedLongToLong(-1);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
    try {
      testReadUnsignedLongToLong(Long.MIN_VALUE);
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  private static void testReadUnsignedLongToLong(long testValue) {
    ParsableByteArray testArray = new ParsableByteArray(
        ByteBuffer.allocate(8).putLong(testValue).array());
    long readValue = testArray.readUnsignedLongToLong();

    // Assert that the value we read was the value we wrote.
    assertEquals(testValue, readValue);
    // And that the position advanced as expected.
    assertEquals(8, testArray.getPosition());

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-8);
    readValue = testArray.readUnsignedLongToLong();
    assertEquals(testValue, readValue);
    assertEquals(8, testArray.getPosition());
  }

  public void testReadLong() {
    testReadLong(0);
    testReadLong(1);
    testReadLong(-1);
    testReadLong(Long.MIN_VALUE);
    testReadLong(Long.MAX_VALUE);
  }

  private static void testReadLong(long testValue) {
    ParsableByteArray testArray = new ParsableByteArray(
        ByteBuffer.allocate(8).putLong(testValue).array());
    long readValue = testArray.readLong();

    // Assert that the value we read was the value we wrote.
    assertEquals(testValue, readValue);
    // And that the position advanced as expected.
    assertEquals(8, testArray.getPosition());

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-8);
    readValue = testArray.readLong();
    assertEquals(testValue, readValue);
    assertEquals(8, testArray.getPosition());
  }

  public void testReadingMovesPosition() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // Given an array at the start
    assertEquals(0, parsableByteArray.getPosition());
    // When reading an integer, the position advances
    parsableByteArray.readUnsignedInt();
    assertEquals(4, parsableByteArray.getPosition());
  }

  public void testOutOfBoundsThrows() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // Given an array at the end
    parsableByteArray.readUnsignedLongToLong();
    assertEquals(TEST_DATA.length, parsableByteArray.getPosition());
    // Then reading more data throws.
    try {
      parsableByteArray.readUnsignedInt();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  public void testModificationsAffectParsableArray() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // When modifying the wrapped byte array
    byte[] data = parsableByteArray.data;
    long readValue = parsableByteArray.readUnsignedInt();
    data[0] = (byte) (TEST_DATA[0] + 1);
    parsableByteArray.setPosition(0);
    // Then the parsed value changes.
    assertFalse(parsableByteArray.readUnsignedInt() == readValue);
  }

  public void testReadingUnsignedLongWithMsbSetThrows() {
    ParsableByteArray parsableByteArray = getTestDataArray();

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
    ParsableByteArray parsableByteArray = getTestDataArray();

    // When reading the integer part of a 16.16 fixed point value
    int value = parsableByteArray.readUnsignedFixedPoint1616();
    // Then the read value is equal to the array elements interpreted as a short.
    assertEquals((0xFF & TEST_DATA[0]) << 8 | (TEST_DATA[1] & 0xFF), value);
    assertEquals(4, parsableByteArray.getPosition());
  }

  public void testReadingBytesReturnsCopy() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // When reading all the bytes back
    int length = parsableByteArray.limit();
    assertEquals(TEST_DATA.length, length);
    byte[] copy = new byte[length];
    parsableByteArray.readBytes(copy, 0, length);
    // Then the array elements are the same.
    assertTrue(Arrays.equals(parsableByteArray.data, copy));
  }

}
