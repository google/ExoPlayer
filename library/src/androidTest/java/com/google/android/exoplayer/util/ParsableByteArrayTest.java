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
import java.nio.charset.Charset;
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

  public void testReadShort() {
    testReadShort((short) -1);
    testReadShort((short) 0);
    testReadShort((short) 1);
    testReadShort(Short.MIN_VALUE);
    testReadShort(Short.MAX_VALUE);
  }

  private static void testReadShort(short testValue) {
    ParsableByteArray testArray = new ParsableByteArray(
        ByteBuffer.allocate(4).putShort(testValue).array());
    int readValue = testArray.readShort();

    // Assert that the value we read was the value we wrote.
    assertEquals(testValue, readValue);
    // And that the position advanced as expected.
    assertEquals(2, testArray.getPosition());

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-2);
    readValue = testArray.readShort();
    assertEquals(testValue, readValue);
    assertEquals(2, testArray.getPosition());
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

  public void testReadLittleEndianLong() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[]{
        0x01, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, (byte) 0xFF
    });
    assertEquals(0xFF00000000000001L, byteArray.readLittleEndianLong());
    assertEquals(8, byteArray.getPosition());
  }

  public void testReadLittleEndianUnsignedInt() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[] {
        0x10, 0x00, 0x00, (byte) 0xFF
    });
    assertEquals(0xFF000010L, byteArray.readLittleEndianUnsignedInt());
    assertEquals(4, byteArray.getPosition());
  }

  public void testReadLittleEndianInt() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[]{
        0x01, 0x00, 0x00, (byte) 0xFF
    });
    assertEquals(0xFF000001, byteArray.readLittleEndianInt());
    assertEquals(4, byteArray.getPosition());
  }

  public void testReadLittleEndianUnsignedInt24() {
    byte[] data = { 0x01, 0x02, (byte) 0xFF };
    ParsableByteArray byteArray = new ParsableByteArray(data);
    assertEquals(0xFF0201, byteArray.readLittleEndianUnsignedInt24());
    assertEquals(3, byteArray.getPosition());
  }

  public void testReadLittleEndianUnsignedShort() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[]{
        0x01, (byte) 0xFF, 0x02, (byte) 0xFF
    });
    assertEquals(0xFF01, byteArray.readLittleEndianUnsignedShort());
    assertEquals(2, byteArray.getPosition());
    assertEquals(0xFF02, byteArray.readLittleEndianUnsignedShort());
    assertEquals(4, byteArray.getPosition());
  }

  public void testReadLittleEndianShort() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[]{
        0x01, (byte) 0xFF, 0x02, (byte) 0xFF
    });
    assertEquals((short) 0xFF01, byteArray.readLittleEndianShort());
    assertEquals(2, byteArray.getPosition());
    assertEquals((short) 0xFF02, byteArray.readLittleEndianShort());
    assertEquals(4, byteArray.getPosition());
  }

  public void testReadString() {
    byte[] data = {
        (byte) 0xC3, (byte) 0xA4, (byte) 0x20,
        (byte) 0xC3, (byte) 0xB6, (byte) 0x20,
        (byte) 0xC2, (byte) 0xAE, (byte) 0x20,
        (byte) 0xCF, (byte) 0x80, (byte) 0x20,
        (byte) 0xE2, (byte) 0x88, (byte) 0x9A, (byte) 0x20,
        (byte) 0xC2, (byte) 0xB1, (byte) 0x20,
        (byte) 0xE8, (byte) 0xB0, (byte) 0xA2, (byte) 0x20,
    };
    ParsableByteArray byteArray = new ParsableByteArray(data);
    assertEquals("ä ö ® π √ ± 谢 ", byteArray.readString(data.length));
    assertEquals(data.length, byteArray.getPosition());
  }

  public void testReadAsciiString() {
    byte[] data = new byte[] {'t', 'e', 's', 't'};
    ParsableByteArray testArray = new ParsableByteArray(data);
    assertEquals("test", testArray.readString(data.length, Charset.forName("US-ASCII")));
    assertEquals(data.length, testArray.getPosition());
  }

  public void testReadStringOutOfBoundsDoesNotMovePosition() {
    byte[] data = {
        (byte) 0xC3, (byte) 0xA4, (byte) 0x20
    };
    ParsableByteArray byteArray = new ParsableByteArray(data);
    try {
      byteArray.readString(data.length + 1);
      fail();
    } catch (StringIndexOutOfBoundsException e) {
      assertEquals(0, byteArray.getPosition());
    }
  }

  public void testReadEmptyString() {
    byte[] bytes = new byte[0];
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertNull(parser.readLine());
  }

  public void testReadSingleLineWithoutEndingTrail() {
    byte[] bytes = new byte[] {
      'f', 'o', 'o'
    };
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertEquals("foo", parser.readLine());
    assertNull(parser.readLine());
  }

  public void testReadSingleLineWithEndingLf() {
    byte[] bytes = new byte[] {
      'f', 'o', 'o', '\n'
    };
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertEquals("foo", parser.readLine());
    assertNull(parser.readLine());
  }

  public void testReadTwoLinesWithCrFollowedByLf() {
    byte[] bytes = new byte[] {
      'f', 'o', 'o', '\r', '\n', 'b', 'a', 'r'
    };
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertEquals("foo", parser.readLine());
    assertEquals("bar", parser.readLine());
    assertNull(parser.readLine());
  }

  public void testReadThreeLinesWithEmptyLine() {
    byte[] bytes = new byte[] {
      'f', 'o', 'o', '\r', '\n', '\r', 'b', 'a', 'r'
    };
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertEquals("foo", parser.readLine());
    assertEquals("", parser.readLine());
    assertEquals("bar", parser.readLine());
    assertNull(parser.readLine());
  }

  public void testReadFourLinesWithLfFollowedByCr() {
    byte[] bytes = new byte[] {
      'f', 'o', 'o', '\n', '\r', '\r', 'b', 'a', 'r', '\r', '\n'
    };
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertEquals("foo", parser.readLine());
    assertEquals("", parser.readLine());
    assertEquals("", parser.readLine());
    assertEquals("bar", parser.readLine());
    assertNull(parser.readLine());
  }

}
