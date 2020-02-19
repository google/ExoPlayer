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

import android.util.Log;

import junit.framework.Test;
import junit.framework.TestCase;

/**
 * Tests for {@link ParsableByteArray}.
 */
public class CircularByteQueueTest extends TestCase {

  private static final byte[] TEST_DATA =
      new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};

  public  void testEmptyRead() {
    CircularByteQueue testQueue = new CircularByteQueue(8);

    // queue is empty. must fail
    assertFalse(testQueue.canRead(1));

    for (int i = 0; i < TEST_DATA.length; i++) {
      assertTrue(testQueue.write(TEST_DATA[i]));
    }
    // queue is full - check size
    assertEquals(testQueue.size(), TEST_DATA.length);

    assertTrue(testQueue.canRead(1));
    assertTrue(testQueue.canRead(8));

    // cannot read beyond queue size
    assertFalse(testQueue.canRead(9));

    for (int i = 0; i < TEST_DATA.length; i++) {
      assertTrue(testQueue.canRead(1));
      assertEquals(TEST_DATA[i],testQueue.read());
    }
    // queue is empty- check can read again
    assertFalse(testQueue.canRead(1));
  }

  public  void testFullWrite() {
    CircularByteQueue testQueue = new CircularByteQueue(8);

    assertFalse(testQueue.canRead(1));

    for (int i = 0; i < TEST_DATA.length; i++) {
      assertTrue(testQueue.write(TEST_DATA[i]));
    }
    // queue is full here

    // writing when queue is full should fail
    assertFalse(testQueue.write(TEST_DATA[0]));

    // read one byte - check it matches first queued data
    assertEquals(testQueue.read(), TEST_DATA[0]);

    // queue has  1 space now

    // now write should succeed
    assertTrue(testQueue.write(TEST_DATA[0]));

    // queue is full now
    // writing when queue is full should fail
    assertFalse(testQueue.write(TEST_DATA[0]));

  }

  public  void testReadWriteCircular1() {
    CircularByteQueue testQueue = new CircularByteQueue(8);
    // write two and read two and check sanity
    for (int i = 0; i < TEST_DATA.length / 2; i += 2) {
      assertTrue(testQueue.write(TEST_DATA[i]));
      assertTrue(testQueue.write(TEST_DATA[i + 1]));
      assertEquals(testQueue.read(),TEST_DATA[i]);
      assertEquals(testQueue.read(),TEST_DATA[i + 1]);
    }
    assertEquals(testQueue.size(), 0);
    // starts wrapping around, write two and read two
    for (int i = 0; i < TEST_DATA.length / 2; i += 2) {
      assertTrue(testQueue.write(TEST_DATA[i]));
      assertTrue(testQueue.write(TEST_DATA[i + 1]));
      assertEquals(testQueue.read(),TEST_DATA[i]);
      assertEquals(testQueue.read(),TEST_DATA[i + 1]);
    }
    assertEquals(testQueue.size(), 0);
  }
  public  void testReadWriteCircular2() {
    CircularByteQueue testQueue = new CircularByteQueue(8);
    // write 7 bytes
    for (int i = 0; i < 7; i++ ) {
      assertTrue(testQueue.write(TEST_DATA[i]));
    }
    // check size is 7
    assertEquals(testQueue.size(), 7);

    // read 3 bytes
    for (int i = 0; i < 3; i++ ) {
      assertEquals(testQueue.read(), TEST_DATA[i]);
    }
    // check size is 4
    assertEquals(testQueue.size(), 4);

    // write 4 bytes: 7 and 0,1,2
    assertTrue(testQueue.write(TEST_DATA[7]));
    for (int i = 0; i < 3; i++ ) {
      assertTrue(testQueue.write(TEST_DATA[i]));
    }
    // check you can't write further
    assertFalse(testQueue.write(TEST_DATA[7]));
    // read all
    for (int i = 3; i < 8; i++ ) {
      assertEquals(testQueue.read(), TEST_DATA[i]);
    }
    for (int i = 0; i < 3; i++ ) {
      assertEquals(testQueue.read(), TEST_DATA[i]);
    }
    // check queue is empty
    assertEquals(testQueue.size(), 0);
  }
}