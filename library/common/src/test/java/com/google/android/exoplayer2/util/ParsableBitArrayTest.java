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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ParsableBitArray}. */
@RunWith(AndroidJUnit4.class)
public final class ParsableBitArrayTest {

  @Test
  public void readAllBytes() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    byte[] bytesRead = new byte[testData.length];

    testArray.readBytes(bytesRead, 0, testData.length);

    assertThat(bytesRead).isEqualTo(testData);
    assertThat(testArray.getPosition()).isEqualTo(testData.length * 8);
    assertThat(testArray.getBytePosition()).isEqualTo(testData.length);
  }

  @Test
  public void readBitInSameByte() {
    byte[] testData = createByteArray(0, 0b00110000);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.setPosition(10);

    assertThat(testArray.readBit()).isTrue();
    assertThat(testArray.readBit()).isTrue();
    assertThat(testArray.readBit()).isFalse();
    assertThat(testArray.readBit()).isFalse();
  }

  @Test
  public void readBitInMultipleBytes() {
    byte[] testData = createByteArray(1, 1 << 7);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.setPosition(6);

    assertThat(testArray.readBit()).isFalse();
    assertThat(testArray.readBit()).isTrue();
    assertThat(testArray.readBit()).isTrue();
    assertThat(testArray.readBit()).isFalse();
  }

  @Test
  public void readBits0Bits() {
    byte[] testData = createByteArray(0x3C);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    int result = testArray.readBits(0);

    assertThat(result).isEqualTo(0);
  }

  @Test
  public void readBitsByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.readBits(8);

    int result = testArray.readBits(18);

    assertThat(result).isEqualTo(0xD25F << 2);
    assertThat(testArray.getPosition()).isEqualTo(26);
  }

  @Test
  public void readBitsNonByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.readBits(3);

    int result = testArray.readBits(14);

    assertThat(result).isEqualTo((0x3C & 0b11111) << 9 | 0xD2 << 1 | 0x5F >> 7);
    assertThat(testArray.getPosition()).isEqualTo(17);
  }

  @Test
  public void readBitsNegativeValue() {
    byte[] testData = createByteArray(0xF0, 0, 0, 0);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    int result = testArray.readBits(32);

    assertThat(result).isEqualTo(0xF0000000);
  }

  @Test
  public void readBitsToLong0Bits() {
    byte[] testData = createByteArray(0x3C);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    long result = testArray.readBitsToLong(0);

    assertThat(result).isEqualTo(0);
  }

  @Test
  public void readBitsToLongByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01, 0xFF, 0x14, 0x60);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.readBits(8);

    long result = testArray.readBitsToLong(45);

    assertThat(result).isEqualTo(0xD25F01FF14L << 5 | 0x60 >> 3);
    assertThat(testArray.getPosition()).isEqualTo(53);
  }

  @Test
  public void readBitsToLongNonByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01, 0xFF, 0x14, 0x60);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.readBits(3);

    long result = testArray.readBitsToLong(53);

    assertThat(result).isEqualTo((0x3CL & 0b11111) << 48 | 0xD25F01FF1460L);
    assertThat(testArray.getPosition()).isEqualTo(56);
  }

  @Test
  public void readBitsToLongNegativeValue() {
    byte[] testData = createByteArray(0xF0, 0, 0, 0, 0, 0, 0, 0);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    long result = testArray.readBitsToLong(64);

    assertThat(result).isEqualTo(0xF000000000000000L);
  }

  @Test
  public void readBitsToByteArray() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01, 0xFF, 0x14, 0x60, 0x99);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    int numBytes = testData.length;
    byte[] result = new byte[numBytes];
    // Test read within byte boundaries.
    testArray.readBits(result, 0, 6);
    assertThat(result[0]).isEqualTo((byte) (testData[0] & 0xFC));
    // Test read across byte boundaries.
    testArray.readBits(result, 0, 8);
    assertThat(result[0])
        .isEqualTo((byte) (((testData[0] & 0x03) << 6) | ((testData[1] & 0xFC) >> 2)));
    // Test reading across multiple bytes.
    testArray.readBits(result, 1, numBytes * 8 - 14);
    for (int i = 1; i < numBytes - 1; i++) {
      assertThat(result[i])
          .isEqualTo((byte) (((testData[i] & 0x03) << 6) | ((testData[i + 1] & 0xFC) >> 2)));
    }
    assertThat(result[numBytes - 1]).isEqualTo((byte) ((testData[numBytes - 1] & 0x03) << 6));
    assertThat(testArray.bitsLeft()).isEqualTo(0);
    // Test read last buffer byte across input data bytes.
    testArray.setPosition(31);
    result[3] = 0;
    testArray.readBits(result, 3, 3);
    assertThat(result[3]).isEqualTo((byte) 0xE0);
    // Test read bits in the middle of a input data byte.
    result[0] = 0;
    assertThat(testArray.getPosition()).isEqualTo(34);
    testArray.readBits(result, 0, 3);
    assertThat(result[0]).isEqualTo((byte) 0xE0);
    // Test read 0 bits.
    testArray.setPosition(32);
    result[1] = 0;
    testArray.readBits(result, 1, 0);
    assertThat(result[1]).isEqualTo((byte) 0);
    // Test reading a number of bits divisible by 8.
    testArray.setPosition(0);
    testArray.readBits(result, 0, 16);
    assertThat(result[0]).isEqualTo(testData[0]);
    assertThat(result[1]).isEqualTo(testData[1]);
    // Test least significant bits are unmodified.
    result[1] = (byte) 0xFF;
    testArray.readBits(result, 0, 9);
    assertThat(result[0]).isEqualTo((byte) 0x5F);
    assertThat(result[1]).isEqualTo((byte) 0x7F);
  }

  @Test
  public void skipBytes() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.skipBytes(2);

    assertThat(testArray.readBits(8)).isEqualTo(0x5F);
  }

  @Test
  public void skipBitsByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.skipBits(16);

    assertThat(testArray.readBits(8)).isEqualTo(0x5F);
  }

  @Test
  public void skipBitsNonByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.skipBits(5);

    assertThat(testArray.readBits(11)).isEqualTo((0x3C & 0b111) << 8 | 0xD2);
  }

  @Test
  public void setPositionByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.setPosition(16);

    assertThat(testArray.readBits(8)).isEqualTo(0x5F);
  }

  @Test
  public void setPositionNonByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.setPosition(5);

    assertThat(testArray.readBits(11)).isEqualTo((0x3C & 0b111) << 8 | 0xD2);
  }

  @Test
  public void byteAlignFromNonByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.setPosition(11);

    testArray.byteAlign();

    assertThat(testArray.getBytePosition()).isEqualTo(2);
    assertThat(testArray.getPosition()).isEqualTo(16);
    assertThat(testArray.readBits(8)).isEqualTo(0x5F);
  }

  @Test
  public void byteAlignFromByteAligned() {
    byte[] testData = createByteArray(0x3C, 0xD2, 0x5F, 0x01);
    ParsableBitArray testArray = new ParsableBitArray(testData);
    testArray.setPosition(16);

    testArray.byteAlign(); // Should be a no-op.

    assertThat(testArray.getBytePosition()).isEqualTo(2);
    assertThat(testArray.getPosition()).isEqualTo(16);
    assertThat(testArray.readBits(8)).isEqualTo(0x5F);
  }

  @Test
  public void readBytesAsStringDefaultsToUtf8() {
    byte[] testData = "a non-åscii strìng".getBytes(Charsets.UTF_8);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.skipBytes(2);
    assertThat(testArray.readBytesAsString(testData.length - 2)).isEqualTo("non-åscii strìng");
  }

  @Test
  public void readBytesAsStringExplicitCharset() {
    byte[] testData = "a non-åscii strìng".getBytes(Charsets.UTF_16);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.skipBytes(6);
    assertThat(testArray.readBytesAsString(testData.length - 6, Charsets.UTF_16))
        .isEqualTo("non-åscii strìng");
  }

  @Test
  public void readBytesNotByteAligned() {
    String testString = "test string";
    byte[] testData = testString.getBytes(Charsets.UTF_8);
    ParsableBitArray testArray = new ParsableBitArray(testData);

    testArray.skipBit();
    assertThrows(IllegalStateException.class, () -> testArray.readBytesAsString(2));
  }

  @Test
  public void putBitsWithinByte() {
    ParsableBitArray output = new ParsableBitArray(new byte[4]);
    output.skipBits(1);

    output.putInt(0x3F, 5);

    output.setPosition(0);
    assertThat(output.readBits(8)).isEqualTo(0x1F << 2); // Check that only 5 bits are modified.
  }

  @Test
  public void putBitsAcrossTwoBytes() {
    ParsableBitArray output = new ParsableBitArray(new byte[4]);
    output.setPosition(12);

    output.putInt(0xFF, 8);

    output.setPosition(8);
    assertThat(output.readBits(16)).isEqualTo(0x0FF0);
  }

  @Test
  public void putBitsAcrossMultipleBytes() {
    ParsableBitArray output = new ParsableBitArray(new byte[8]);
    output.setPosition(31); // Writing starts at 31 to test the 30th bit is not modified.

    output.putInt(0xFF146098, 30); // Write only 30 to test the 61st bit is not modified.

    output.setPosition(30);
    assertThat(output.readBits(32)).isEqualTo(0x3F146098 << 1);
  }

  @Test
  public void put32Bits() {
    ParsableBitArray output = new ParsableBitArray(new byte[5]);
    output.setPosition(4);

    output.putInt(0xFF146098, 32);

    output.setPosition(4);
    assertThat(output.readBits(32)).isEqualTo(0xFF146098);
  }

  @Test
  public void putFullBytes() {
    ParsableBitArray output = new ParsableBitArray(new byte[2]);

    output.putInt(0x81, 8);

    output.setPosition(0);
    assertThat(output.readBits(8)).isEqualTo(0x81);
  }

  @Test
  public void noOverwriting() {
    ParsableBitArray output = new ParsableBitArray(createByteArray(0xFF, 0xFF, 0xFF, 0xFF, 0xFF));
    output.setPosition(1);

    output.putInt(0, 30);

    output.setPosition(0);
    assertThat(output.readBits(32)).isEqualTo(0x80000001);
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
