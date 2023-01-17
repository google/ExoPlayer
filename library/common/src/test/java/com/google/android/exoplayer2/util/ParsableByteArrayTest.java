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
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.Charset.forName;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Charsets;
import com.google.common.primitives.Bytes;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ParsableByteArray}. */
@RunWith(AndroidJUnit4.class)
public final class ParsableByteArrayTest {

  private static final byte[] TEST_DATA =
      new byte[] {0x0F, (byte) 0xFF, (byte) 0x42, (byte) 0x0F, 0x00, 0x00, 0x00, 0x00};

  private static ParsableByteArray getTestDataArray() {
    ParsableByteArray testArray = new ParsableByteArray(TEST_DATA.length);
    System.arraycopy(TEST_DATA, 0, testArray.getData(), 0, TEST_DATA.length);
    return testArray;
  }

  @Test
  public void ensureCapacity_doesntReallocateNeedlesslyAndPreservesPositionAndLimit() {
    ParsableByteArray array = getTestDataArray();
    byte[] dataBefore = array.getData();
    byte[] copyOfDataBefore = dataBefore.clone();

    array.setPosition(3);
    array.setLimit(4);
    array.ensureCapacity(array.capacity() - 1);

    assertThat(array.getData()).isSameInstanceAs(dataBefore);
    assertThat(array.getData()).isEqualTo(copyOfDataBefore);
    assertThat(array.getPosition()).isEqualTo(3);
    assertThat(array.limit()).isEqualTo(4);
  }

  @Test
  public void ensureCapacity_preservesDataPositionAndLimitWhenReallocating() {
    ParsableByteArray array = getTestDataArray();
    byte[] copyOfDataBefore = array.getData().clone();

    array.setPosition(3);
    array.setLimit(4);
    array.ensureCapacity(array.capacity() + 1);

    assertThat(array.getData()).isEqualTo(Bytes.concat(copyOfDataBefore, new byte[] {0}));
    assertThat(array.getPosition()).isEqualTo(3);
    assertThat(array.limit()).isEqualTo(4);
  }

  @Test
  public void readShort() {
    testReadShort((short) -1);
    testReadShort((short) 0);
    testReadShort((short) 1);
    testReadShort(Short.MIN_VALUE);
    testReadShort(Short.MAX_VALUE);
  }

  private static void testReadShort(short testValue) {
    ParsableByteArray testArray =
        new ParsableByteArray(ByteBuffer.allocate(4).putShort(testValue).array());
    int readValue = testArray.readShort();

    // Assert that the value we read was the value we wrote.
    assertThat(readValue).isEqualTo(testValue);
    // And that the position advanced as expected.
    assertThat(testArray.getPosition()).isEqualTo(2);

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-2);
    readValue = testArray.readShort();
    assertThat(readValue).isEqualTo(testValue);
    assertThat(testArray.getPosition()).isEqualTo(2);
  }

  @Test
  public void readInt() {
    testReadInt(0);
    testReadInt(1);
    testReadInt(-1);
    testReadInt(Integer.MIN_VALUE);
    testReadInt(Integer.MAX_VALUE);
  }

  private static void testReadInt(int testValue) {
    ParsableByteArray testArray =
        new ParsableByteArray(ByteBuffer.allocate(4).putInt(testValue).array());
    int readValue = testArray.readInt();

    // Assert that the value we read was the value we wrote.
    assertThat(readValue).isEqualTo(testValue);
    // And that the position advanced as expected.
    assertThat(testArray.getPosition()).isEqualTo(4);

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-4);
    readValue = testArray.readInt();
    assertThat(readValue).isEqualTo(testValue);
    assertThat(testArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readUnsignedInt() {
    testReadUnsignedInt(0);
    testReadUnsignedInt(1);
    testReadUnsignedInt(Integer.MAX_VALUE);
    testReadUnsignedInt(Integer.MAX_VALUE + 1L);
    testReadUnsignedInt(0xFFFFFFFFL);
  }

  private static void testReadUnsignedInt(long testValue) {
    ParsableByteArray testArray =
        new ParsableByteArray(
            Arrays.copyOfRange(ByteBuffer.allocate(8).putLong(testValue).array(), 4, 8));
    long readValue = testArray.readUnsignedInt();

    // Assert that the value we read was the value we wrote.
    assertThat(readValue).isEqualTo(testValue);
    // And that the position advanced as expected.
    assertThat(testArray.getPosition()).isEqualTo(4);

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-4);
    readValue = testArray.readUnsignedInt();
    assertThat(readValue).isEqualTo(testValue);
    assertThat(testArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readUnsignedIntToInt() {
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
    ParsableByteArray testArray =
        new ParsableByteArray(ByteBuffer.allocate(4).putInt(testValue).array());
    int readValue = testArray.readUnsignedIntToInt();

    // Assert that the value we read was the value we wrote.
    assertThat(readValue).isEqualTo(testValue);
    // And that the position advanced as expected.
    assertThat(testArray.getPosition()).isEqualTo(4);

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-4);
    readValue = testArray.readUnsignedIntToInt();
    assertThat(readValue).isEqualTo(testValue);
    assertThat(testArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readUnsignedLongToLong() {
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
    ParsableByteArray testArray =
        new ParsableByteArray(ByteBuffer.allocate(8).putLong(testValue).array());
    long readValue = testArray.readUnsignedLongToLong();

    // Assert that the value we read was the value we wrote.
    assertThat(readValue).isEqualTo(testValue);
    // And that the position advanced as expected.
    assertThat(testArray.getPosition()).isEqualTo(8);

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-8);
    readValue = testArray.readUnsignedLongToLong();
    assertThat(readValue).isEqualTo(testValue);
    assertThat(testArray.getPosition()).isEqualTo(8);
  }

  @Test
  public void readLong() {
    testReadLong(0);
    testReadLong(1);
    testReadLong(-1);
    testReadLong(Long.MIN_VALUE);
    testReadLong(Long.MAX_VALUE);
  }

  private static void testReadLong(long testValue) {
    ParsableByteArray testArray =
        new ParsableByteArray(ByteBuffer.allocate(8).putLong(testValue).array());
    long readValue = testArray.readLong();

    // Assert that the value we read was the value we wrote.
    assertThat(readValue).isEqualTo(testValue);
    // And that the position advanced as expected.
    assertThat(testArray.getPosition()).isEqualTo(8);

    // And that skipping back and reading gives the same results.
    testArray.skipBytes(-8);
    readValue = testArray.readLong();
    assertThat(readValue).isEqualTo(testValue);
    assertThat(testArray.getPosition()).isEqualTo(8);
  }

  @Test
  public void readingMovesPosition() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // Given an array at the start
    assertThat(parsableByteArray.getPosition()).isEqualTo(0);
    // When reading an integer, the position advances
    parsableByteArray.readUnsignedInt();
    assertThat(parsableByteArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void outOfBoundsThrows() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // Given an array at the end
    parsableByteArray.readUnsignedLongToLong();
    assertThat(parsableByteArray.getPosition()).isEqualTo(TEST_DATA.length);
    // Then reading more data throws.
    try {
      parsableByteArray.readUnsignedInt();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void modificationsAffectParsableArray() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // When modifying the wrapped byte array
    byte[] data = parsableByteArray.getData();
    long readValue = parsableByteArray.readUnsignedInt();
    data[0] = (byte) (TEST_DATA[0] + 1);
    parsableByteArray.setPosition(0);
    // Then the parsed value changes.
    assertThat(parsableByteArray.readUnsignedInt()).isNotEqualTo(readValue);
  }

  @Test
  public void readingUnsignedLongWithMsbSetThrows() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // Given an array with the most-significant bit set on the top byte
    byte[] data = parsableByteArray.getData();
    data[0] = (byte) 0x80;
    // Then reading an unsigned long throws.
    try {
      parsableByteArray.readUnsignedLongToLong();
      fail();
    } catch (Exception e) {
      // Expected.
    }
  }

  @Test
  public void readUnsignedFixedPoint1616() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // When reading the integer part of a 16.16 fixed point value
    int value = parsableByteArray.readUnsignedFixedPoint1616();
    // Then the read value is equal to the array elements interpreted as a short.
    assertThat(value).isEqualTo((0xFF & TEST_DATA[0]) << 8 | (TEST_DATA[1] & 0xFF));
    assertThat(parsableByteArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readingBytesReturnsCopy() {
    ParsableByteArray parsableByteArray = getTestDataArray();

    // When reading all the bytes back
    int length = parsableByteArray.limit();
    assertThat(length).isEqualTo(TEST_DATA.length);
    byte[] copy = new byte[length];
    parsableByteArray.readBytes(copy, 0, length);
    // Then the array elements are the same.
    assertThat(copy).isEqualTo(parsableByteArray.getData());
  }

  @Test
  public void readLittleEndianLong() {
    ParsableByteArray byteArray =
        new ParsableByteArray(new byte[] {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF});

    assertThat(byteArray.readLittleEndianLong()).isEqualTo(0xFF00000000000001L);
    assertThat(byteArray.getPosition()).isEqualTo(8);
  }

  @Test
  public void readLittleEndianUnsignedInt() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[] {0x10, 0x00, 0x00, (byte) 0xFF});

    assertThat(byteArray.readLittleEndianUnsignedInt()).isEqualTo(0xFF000010L);
    assertThat(byteArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readLittleEndianInt() {
    ParsableByteArray byteArray = new ParsableByteArray(new byte[] {0x01, 0x00, 0x00, (byte) 0xFF});

    assertThat(byteArray.readLittleEndianInt()).isEqualTo(0xFF000001);
    assertThat(byteArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readLittleEndianUnsignedInt24() {
    byte[] data = {0x01, 0x02, (byte) 0xFF};
    ParsableByteArray byteArray = new ParsableByteArray(data);

    assertThat(byteArray.readLittleEndianUnsignedInt24()).isEqualTo(0xFF0201);
    assertThat(byteArray.getPosition()).isEqualTo(3);
  }

  @Test
  public void readInt24Positive() {
    byte[] data = {0x01, 0x02, (byte) 0xFF};
    ParsableByteArray byteArray = new ParsableByteArray(data);

    assertThat(byteArray.readInt24()).isEqualTo(0x0102FF);
    assertThat(byteArray.getPosition()).isEqualTo(3);
  }

  @Test
  public void readInt24Negative() {
    byte[] data = {(byte) 0xFF, 0x02, (byte) 0x01};
    ParsableByteArray byteArray = new ParsableByteArray(data);

    assertThat(byteArray.readInt24()).isEqualTo(0xFFFF0201);
    assertThat(byteArray.getPosition()).isEqualTo(3);
  }

  @Test
  public void readLittleEndianUnsignedShort() {
    ParsableByteArray byteArray =
        new ParsableByteArray(new byte[] {0x01, (byte) 0xFF, 0x02, (byte) 0xFF});

    assertThat(byteArray.readLittleEndianUnsignedShort()).isEqualTo(0xFF01);
    assertThat(byteArray.getPosition()).isEqualTo(2);
    assertThat(byteArray.readLittleEndianUnsignedShort()).isEqualTo(0xFF02);
    assertThat(byteArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readLittleEndianShort() {
    ParsableByteArray byteArray =
        new ParsableByteArray(new byte[] {0x01, (byte) 0xFF, 0x02, (byte) 0xFF});

    assertThat(byteArray.readLittleEndianShort()).isEqualTo((short) 0xFF01);
    assertThat(byteArray.getPosition()).isEqualTo(2);
    assertThat(byteArray.readLittleEndianShort()).isEqualTo((short) 0xFF02);
    assertThat(byteArray.getPosition()).isEqualTo(4);
  }

  @Test
  public void readString() {
    byte[] data = {
      (byte) 0xC3,
      (byte) 0xA4,
      (byte) 0x20,
      (byte) 0xC3,
      (byte) 0xB6,
      (byte) 0x20,
      (byte) 0xC2,
      (byte) 0xAE,
      (byte) 0x20,
      (byte) 0xCF,
      (byte) 0x80,
      (byte) 0x20,
      (byte) 0xE2,
      (byte) 0x88,
      (byte) 0x9A,
      (byte) 0x20,
      (byte) 0xC2,
      (byte) 0xB1,
      (byte) 0x20,
      (byte) 0xE8,
      (byte) 0xB0,
      (byte) 0xA2,
      (byte) 0x20,
    };
    ParsableByteArray byteArray = new ParsableByteArray(data);

    assertThat(byteArray.readString(data.length)).isEqualTo("ä ö ® π √ ± 谢 ");
    assertThat(byteArray.getPosition()).isEqualTo(data.length);
  }

  @Test
  public void readAsciiString() {
    byte[] data = new byte[] {'t', 'e', 's', 't'};
    ParsableByteArray testArray = new ParsableByteArray(data);

    assertThat(testArray.readString(data.length, forName("US-ASCII"))).isEqualTo("test");
    assertThat(testArray.getPosition()).isEqualTo(data.length);
  }

  @Test
  public void readStringOutOfBoundsDoesNotMovePosition() {
    byte[] data = {(byte) 0xC3, (byte) 0xA4, (byte) 0x20};
    ParsableByteArray byteArray = new ParsableByteArray(data);

    try {
      byteArray.readString(data.length + 1);
      fail();
    } catch (StringIndexOutOfBoundsException e) {
      assertThat(byteArray.getPosition()).isEqualTo(0);
    }
  }

  @Test
  public void readEmptyString() {
    byte[] bytes = new byte[0];
    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertThat(parser.readLine()).isNull();
  }

  @Test
  public void readNullTerminatedStringWithLengths_readLengthsMatchNullPositions() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};

    ParsableByteArray parser = new ParsableByteArray(bytes);
    assertThat(parser.readNullTerminatedString(4)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readNullTerminatedString(4)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedStringWithLengths_readLengthsDontMatchNullPositions() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readNullTerminatedString(2)).isEqualTo("fo");
    assertThat(parser.getPosition()).isEqualTo(2);
    assertThat(parser.readNullTerminatedString(2)).isEqualTo("o");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readNullTerminatedString(3)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(7);
    assertThat(parser.readNullTerminatedString(1)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedStringWithLengths_limitAtNull() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
    ParsableByteArray parser = new ParsableByteArray(bytes, /* limit= */ 4);

    assertThat(parser.readNullTerminatedString(4)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedStringWithLengths_limitBeforeNull() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
    ParsableByteArray parser = new ParsableByteArray(bytes, /* limit= */ 3);

    assertThat(parser.readNullTerminatedString(3)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(3);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedString() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readNullTerminatedString()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readNullTerminatedString()).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedString_withLimitAtNull() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
    ParsableByteArray parser = new ParsableByteArray(bytes, /* limit= */ 4);

    assertThat(parser.readNullTerminatedString()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedString_withLimitBeforeNull() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r', 0};
    ParsableByteArray parser = new ParsableByteArray(bytes, /* limit= */ 3);

    assertThat(parser.readNullTerminatedString()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(3);
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readNullTerminatedStringWithoutEndingNull() {
    byte[] bytes = new byte[] {'f', 'o', 'o', 0, 'b', 'a', 'r'};
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readNullTerminatedString()).isEqualTo("foo");
    assertThat(parser.readNullTerminatedString()).isEqualTo("bar");
    assertThat(parser.readNullTerminatedString()).isNull();
  }

  @Test
  public void readDelimiterTerminatedString() {
    byte[] bytes = new byte[] {'f', 'o', 'o', '*', 'b', 'a', 'r', '*'};
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readDelimiterTerminatedString('*')).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readDelimiterTerminatedString('*')).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readDelimiterTerminatedString('*')).isNull();
  }

  @Test
  public void readDelimiterTerminatedString_limitAtDelimiter() {
    byte[] bytes = new byte[] {'f', 'o', 'o', '*', 'b', 'a', 'r', '*'};
    ParsableByteArray parser = new ParsableByteArray(bytes, /* limit= */ 4);

    assertThat(parser.readDelimiterTerminatedString('*')).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readDelimiterTerminatedString('*')).isNull();
  }

  @Test
  public void readDelimiterTerminatedString_limitBeforeDelimiter() {
    byte[] bytes = new byte[] {'f', 'o', 'o', '*', 'b', 'a', 'r', '*'};
    ParsableByteArray parser = new ParsableByteArray(bytes, /* limit= */ 3);

    assertThat(parser.readDelimiterTerminatedString('*')).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(3);
    assertThat(parser.readDelimiterTerminatedString('*')).isNull();
  }

  @Test
  public void readDelimiterTerminatedStringW_noDelimiter() {
    byte[] bytes = new byte[] {'f', 'o', 'o', '*', 'b', 'a', 'r'};
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readDelimiterTerminatedString('*')).isEqualTo("foo");
    assertThat(parser.readDelimiterTerminatedString('*')).isEqualTo("bar");
    assertThat(parser.readDelimiterTerminatedString('*')).isNull();
  }

  @Test
  public void readSingleLineWithoutEndingTrail_ascii() {
    byte[] bytes = "foo".getBytes(Charsets.US_ASCII);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(3);
    assertThat(parser.readLine(Charsets.US_ASCII)).isNull();
  }

  @Test
  public void readSingleLineWithEndingLf_ascii() {
    byte[] bytes = "foo\n".getBytes(Charsets.US_ASCII);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readLine(Charsets.US_ASCII)).isNull();
  }

  @Test
  public void readTwoLinesWithCrFollowedByLf_ascii() {
    byte[] bytes = "foo\r\nbar".getBytes(Charsets.US_ASCII);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(5);
    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.US_ASCII)).isNull();
  }

  @Test
  public void readThreeLinesWithEmptyLine_ascii() {
    byte[] bytes = "foo\r\n\rbar".getBytes(Charsets.US_ASCII);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(5);
    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(6);
    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(9);
    assertThat(parser.readLine(Charsets.US_ASCII)).isNull();
  }

  @Test
  public void readFourLinesWithLfFollowedByCr_ascii() {
    byte[] bytes = "foo\n\r\rbar\r\n".getBytes(Charsets.US_ASCII);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(5);
    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(6);
    assertThat(parser.readLine(Charsets.US_ASCII)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(11);
    assertThat(parser.readLine(Charsets.US_ASCII)).isNull();
  }

  @Test
  public void readSingleLineWithoutEndingTrail_utf8() {
    byte[] bytes = "foo".getBytes(Charsets.UTF_8);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(3);
    assertThat(parser.readLine()).isNull();
  }

  @Test
  public void readSingleLineWithEndingLf_utf8() {
    byte[] bytes = "foo\n".getBytes(Charsets.UTF_8);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readLine()).isNull();
  }

  @Test
  public void readTwoLinesWithCrFollowedByLf_utf8() {
    byte[] bytes = "foo\r\nbar".getBytes(Charsets.UTF_8);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(5);
    assertThat(parser.readLine()).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine()).isNull();
  }

  @Test
  public void readThreeLinesWithEmptyLineAndLeadingBom_utf8() {
    byte[] bytes =
        Bytes.concat(createByteArray(0xEF, 0xBB, 0xBF), "foo\r\n\rbar".getBytes(Charsets.UTF_8));
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine()).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(9);
    assertThat(parser.readLine()).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine()).isNull();
  }

  @Test
  public void readFourLinesWithLfFollowedByCr_utf8() {
    byte[] bytes = "foo\n\r\rbar\r\n".getBytes(Charsets.UTF_8);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine()).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(4);
    assertThat(parser.readLine()).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(5);
    assertThat(parser.readLine()).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(6);
    assertThat(parser.readLine()).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(11);
    assertThat(parser.readLine()).isNull();
  }

  @Test
  public void readSingleLineWithoutEndingTrail_utf16() {
    // Use UTF_16BE because we don't want the leading BOM that's added by getBytes(UTF_16). We
    // explicitly test with a BOM elsewhere.
    byte[] bytes = "foo".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(6);
    assertThat(parser.readLine(Charsets.UTF_16)).isNull();
  }

  @Test
  public void readSingleLineWithEndingLf_utf16() {
    // Use UTF_16BE because we don't want the leading BOM that's added by getBytes(UTF_16). We
    // explicitly test with a BOM elsewhere.
    byte[] bytes = "foo\n".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.UTF_16)).isNull();
  }

  @Test
  public void readTwoLinesWithCrFollowedByLf_utf16() {
    // Use UTF_16BE because we don't want the leading BOM that's added by getBytes(UTF_16). We
    // explicitly test with a BOM elsewhere.
    byte[] bytes = "foo\r\nbar".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(10);
    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(16);
    assertThat(parser.readLine(Charsets.UTF_16)).isNull();
  }

  @Test
  public void readThreeLinesWithEmptyLineAndLeadingBom_utf16() {
    // getBytes(UTF_16) always adds the leading BOM.
    byte[] bytes = "foo\r\n\rbar".getBytes(Charsets.UTF_16);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(14);
    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(20);
    assertThat(parser.readLine(Charsets.UTF_16)).isNull();
  }

  @Test
  public void readFourLinesWithLfFollowedByCr_utf16() {
    // Use UTF_16BE because we don't want the leading BOM that's added by getBytes(UTF_16). We
    // explicitly test with a BOM elsewhere.
    byte[] bytes = "foo\n\r\rbar\r\n".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(10);
    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine(Charsets.UTF_16)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(22);
    assertThat(parser.readLine(Charsets.UTF_16)).isNull();
  }

  @Test
  public void readSingleLineWithoutEndingTrail_utf16be() {
    byte[] bytes = "foo".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(6);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isNull();
  }

  @Test
  public void readSingleLineWithEndingLf_utf16be() {
    byte[] bytes = "foo\n".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isNull();
  }

  @Test
  public void readTwoLinesWithCrFollowedByLf_utf16be() {
    byte[] bytes = "foo\r\nbar".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(10);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(16);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isNull();
  }

  @Test
  public void readThreeLinesWithEmptyLineAndLeadingBom_utf16be() {
    byte[] bytes =
        Bytes.concat(createByteArray(0xFE, 0xFF), "foo\r\n\rbar".getBytes(Charsets.UTF_16BE));
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(14);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(20);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isNull();
  }

  @Test
  public void readFourLinesWithLfFollowedByCr_utf16be() {
    byte[] bytes = "foo\n\r\rbar\r\n".getBytes(Charsets.UTF_16BE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(10);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(22);
    assertThat(parser.readLine(Charsets.UTF_16BE)).isNull();
  }

  @Test
  public void readSingleLineWithoutEndingTrail_utf16le() {
    byte[] bytes = "foo".getBytes(Charsets.UTF_16LE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(6);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isNull();
  }

  @Test
  public void readSingleLineWithEndingLf_utf16le() {
    byte[] bytes = "foo\n".getBytes(Charsets.UTF_16LE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isNull();
  }

  @Test
  public void readTwoLinesWithCrFollowedByLf_utf16le() {
    byte[] bytes = "foo\r\nbar".getBytes(Charsets.UTF_16LE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(10);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(16);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isNull();
  }

  @Test
  public void readThreeLinesWithEmptyLineAndLeadingBom_utf16le() {
    byte[] bytes =
        Bytes.concat(createByteArray(0xFF, 0xFE), "foo\r\n\rbar".getBytes(Charsets.UTF_16LE));
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(14);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(20);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isNull();
  }

  @Test
  public void readFourLinesWithLfFollowedByCr_utf16le() {
    byte[] bytes = "foo\n\r\rbar\r\n".getBytes(Charsets.UTF_16LE);
    ParsableByteArray parser = new ParsableByteArray(bytes);

    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("foo");
    assertThat(parser.getPosition()).isEqualTo(8);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(10);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("");
    assertThat(parser.getPosition()).isEqualTo(12);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isEqualTo("bar");
    assertThat(parser.getPosition()).isEqualTo(22);
    assertThat(parser.readLine(Charsets.UTF_16LE)).isNull();
  }
}
