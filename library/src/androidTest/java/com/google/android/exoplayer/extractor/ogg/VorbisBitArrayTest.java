/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.util.ParsableBitArray;
import junit.framework.TestCase;

/**
 * Unit test for {@link VorbisBitArray}.
 */
public final class VorbisBitArrayTest extends TestCase {

  public void testReadBit() {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0x5c, 0x50
    });

    assertFalse(bitArray.readBit());
    assertFalse(bitArray.readBit());
    assertTrue(bitArray.readBit());
    assertTrue(bitArray.readBit());

    assertTrue(bitArray.readBit());
    assertFalse(bitArray.readBit());
    assertTrue(bitArray.readBit());
    assertFalse(bitArray.readBit());

    assertFalse(bitArray.readBit());
    assertFalse(bitArray.readBit());
    assertFalse(bitArray.readBit());
    assertFalse(bitArray.readBit());

    assertTrue(bitArray.readBit());
    assertFalse(bitArray.readBit());
    assertTrue(bitArray.readBit());
    assertFalse(bitArray.readBit());

    try {
      assertFalse(bitArray.readBit());
      fail();
    } catch (IllegalStateException e) {/* ignored */}
  }

  public void testSkipBits() {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xF0, 0x0F
    });

    bitArray.skipBits(10);
    assertEquals(10, bitArray.getPosition());
    assertTrue(bitArray.readBit());
    assertTrue(bitArray.readBit());
    assertFalse(bitArray.readBit());
    bitArray.skipBits(1);
    assertEquals(14, bitArray.getPosition());
    assertFalse(bitArray.readBit());
    assertFalse(bitArray.readBit());
    try {
      bitArray.readBit();
      fail();
    } catch (IllegalStateException e) {
      // ignored
    }
  }


  public void testSkipBitsThrowsErrorIfEOB() {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xF0, 0x0F
    });

    try {
      bitArray.skipBits(17);
      fail();
    } catch (IllegalStateException e) {/* ignored */}
  }

  public void testGetPosition() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xF0, 0x0F
    });

    assertEquals(0, bitArray.getPosition());
    bitArray.readBit();
    assertEquals(1, bitArray.getPosition());
    bitArray.readBit();
    bitArray.readBit();
    bitArray.skipBits(4);
    assertEquals(7, bitArray.getPosition());
  }

  public void testSetPosition() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xF0, 0x0F
    });

    assertEquals(0, bitArray.getPosition());
    bitArray.setPosition(4);
    assertEquals(4, bitArray.getPosition());

    bitArray.setPosition(15);
    assertFalse(bitArray.readBit());
    try {
      bitArray.readBit();
      fail();
    } catch (IllegalStateException e) {/* ignored */}

  }
  public void testSetPositionIllegalPositions() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xF0, 0x0F
    });

    try {
      bitArray.setPosition(16);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(0, bitArray.getPosition());
    }

    try {
      bitArray.setPosition(-1);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(0, bitArray.getPosition());
    }
  }

  public void testReadInt32() {
    byte[] data = {(byte) 0xF0, 0x0F, (byte) 0xF0, 0x0F};
    VorbisBitArray lsb = new VorbisBitArray(data);
    assertEquals(0x0FF00FF0, lsb.readBits(32));

    data = new byte[]{0x0F, (byte) 0xF0, 0x0F, (byte) 0xF0};
    lsb = new VorbisBitArray(data);
    assertEquals(0xF00FF00F, lsb.readBits(32));
  }

  public void testReadBits() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
      (byte) 0x03, 0x22
    });

    assertEquals(3, bitArray.readBits(2));
    bitArray.skipBits(6);
    assertEquals(2, bitArray.readBits(2));
    bitArray.skipBits(2);
    assertEquals(2, bitArray.readBits(2));

    bitArray.reset();
    assertEquals(0x2203, bitArray.readBits(16));
  }

  public void testRead4BitsBeyondBoundary() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        0x2e, 0x10
    });
    assertEquals(0x2e, bitArray.readBits(7));
    assertEquals(7, bitArray.getPosition());
    assertEquals(0x0, bitArray.readBits(4));
  }

  public void testReadBitsBeyondByteBoundaries() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xFF, (byte) 0x0F, (byte) 0xFF, (byte) 0x0F
    });

    assertEquals(0x0FFF0FFF, bitArray.readBits(32));

    bitArray.reset();
    bitArray.skipBits(4);
    assertEquals(0xF0FF, bitArray.readBits(16));

    bitArray.reset();
    bitArray.skipBits(6);
    assertEquals(0xc3F, bitArray.readBits(12));

    bitArray.reset();
    bitArray.skipBits(6);
    assertTrue(bitArray.readBit());
    assertTrue(bitArray.readBit());
    assertEquals(24, bitArray.bitsLeft());

    bitArray.reset();
    bitArray.skipBits(10);
    assertEquals(3, bitArray.readBits(5));
    assertEquals(15, bitArray.getPosition());
  }

  public void testReadBitsIllegalLengths() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0x03, 0x22, 0x30
    });

    // reading zero bits gets 0 without advancing position
    // (like a zero-bit read is defined to yield zer0)
    assertEquals(0, bitArray.readBits(0));
    assertEquals(0, bitArray.getPosition());
    bitArray.readBit();
    assertEquals(1, bitArray.getPosition());

    try {
      bitArray.readBits(24);
      fail();
    } catch (IllegalStateException e) {
      assertEquals(1, bitArray.getPosition());
    }
  }

  public void testLimit() {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xc0, 0x02
    }, 1);

    try {
      bitArray.skipBits(9);
      fail();
    } catch (IllegalStateException e) {
      assertEquals(0, bitArray.getPosition());
    }

    try {
      bitArray.readBits(9);
      fail();
    } catch (IllegalStateException e) {
      assertEquals(0, bitArray.getPosition());
    }

    bitArray.readBits(8);
    assertEquals(8, bitArray.getPosition());
    try {
      bitArray.readBit();
      fail();
    } catch (IllegalStateException e) {
      assertEquals(8, bitArray.getPosition());
    }
  }

  public void testBitsLeft() {
    VorbisBitArray bitArray = new VorbisBitArray(new byte[]{
        (byte) 0xc0, 0x02
    });
    assertEquals(16, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.skipBits(1);
    assertEquals(15, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.skipBits(3);
    assertEquals(12, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.setPosition(6);
    assertEquals(10, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.skipBits(1);
    assertEquals(9, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.skipBits(1);
    assertEquals(8, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.readBits(4);
    assertEquals(4, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    bitArray.readBits(4);
    assertEquals(0, bitArray.bitsLeft());
    assertEquals(bitArray.limit(), bitArray.getPosition() + bitArray.bitsLeft());

    try {
      bitArray.readBit();
      fail();
    } catch (IllegalStateException e) {
      assertEquals(0, bitArray.bitsLeft());
    }
  }

  public void testReadBitCompareWithMSb() {
    byte[] data = {0x0F};
    VorbisBitArray lsb = new VorbisBitArray(data);
    ParsableBitArray msb = new ParsableBitArray(data);

    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
    assertEquals(lsb.readBit(), !msb.readBit());
  }

  public void testReadBitsCompareWithMSb() {
    byte[] data = {0x0F};
    VorbisBitArray lsb = new VorbisBitArray(data);
    ParsableBitArray msb = new ParsableBitArray(data);

    assertEquals(15, lsb.readBits(4));
    assertEquals(lsb.readBits(4), msb.readBits(4));
    assertEquals(15, msb.readBits(4));
  }

  public void testReadBitsCompareWithMSbBeyondByteBoundary() {
    byte[] data = {(byte) 0xF0, 0x0F};
    VorbisBitArray lsb = new VorbisBitArray(data);
    ParsableBitArray msb = new ParsableBitArray(data);

    assertEquals(0x00, lsb.readBits(4));
    assertEquals(0x0F, msb.readBits(4));

    assertEquals(0xFF, lsb.readBits(8));
    assertEquals(0x00, msb.readBits(8));

    assertEquals(0x00, lsb.readBits(4));
    assertEquals(0x0F, msb.readBits(4));
  }

}
