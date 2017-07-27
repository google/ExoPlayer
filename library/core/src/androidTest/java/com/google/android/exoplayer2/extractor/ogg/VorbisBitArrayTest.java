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
package com.google.android.exoplayer2.extractor.ogg;

import com.google.android.exoplayer2.testutil.TestUtil;
import junit.framework.TestCase;

/**
 * Unit test for {@link VorbisBitArray}.
 */
public final class VorbisBitArrayTest extends TestCase {

  public void testReadBit() {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x5c, 0x50));
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
  }

  public void testSkipBits() {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F));
    bitArray.skipBits(10);
    assertEquals(10, bitArray.getPosition());
    assertTrue(bitArray.readBit());
    assertTrue(bitArray.readBit());
    assertFalse(bitArray.readBit());
    bitArray.skipBits(1);
    assertEquals(14, bitArray.getPosition());
    assertFalse(bitArray.readBit());
    assertFalse(bitArray.readBit());
  }

  public void testGetPosition() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F));
    assertEquals(0, bitArray.getPosition());
    bitArray.readBit();
    assertEquals(1, bitArray.getPosition());
    bitArray.readBit();
    bitArray.readBit();
    bitArray.skipBits(4);
    assertEquals(7, bitArray.getPosition());
  }

  public void testSetPosition() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F));
    assertEquals(0, bitArray.getPosition());
    bitArray.setPosition(4);
    assertEquals(4, bitArray.getPosition());
    bitArray.setPosition(15);
    assertFalse(bitArray.readBit());
  }

  public void testReadInt32() {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xF0, 0x0F, 0xF0, 0x0F));
    assertEquals(0x0FF00FF0, bitArray.readBits(32));
    bitArray = new VorbisBitArray(TestUtil.createByteArray(0x0F, 0xF0, 0x0F, 0xF0));
    assertEquals(0xF00FF00F, bitArray.readBits(32));
  }

  public void testReadBits() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x03, 0x22));
    assertEquals(3, bitArray.readBits(2));
    bitArray.skipBits(6);
    assertEquals(2, bitArray.readBits(2));
    bitArray.skipBits(2);
    assertEquals(2, bitArray.readBits(2));
    bitArray.reset();
    assertEquals(0x2203, bitArray.readBits(16));
  }

  public void testRead4BitsBeyondBoundary() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x2e, 0x10));
    assertEquals(0x2e, bitArray.readBits(7));
    assertEquals(7, bitArray.getPosition());
    assertEquals(0x0, bitArray.readBits(4));
  }

  public void testReadBitsBeyondByteBoundaries() throws Exception {
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0xFF, 0x0F, 0xFF, 0x0F));
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
    VorbisBitArray bitArray = new VorbisBitArray(TestUtil.createByteArray(0x03, 0x22, 0x30));

    // reading zero bits gets 0 without advancing position
    // (like a zero-bit read is defined to yield zer0)
    assertEquals(0, bitArray.readBits(0));
    assertEquals(0, bitArray.getPosition());
    bitArray.readBit();
    assertEquals(1, bitArray.getPosition());
  }

}
