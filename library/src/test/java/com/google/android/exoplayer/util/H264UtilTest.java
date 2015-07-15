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
 * Tests for {@link H264Util}.
 */
public class H264UtilTest extends TestCase {

  private static final int TEST_PARTIAL_NAL_POSITION = 4;
  private static final int TEST_NAL_POSITION = 10;

  public void testFindNalUnit() {
    byte[] data = buildTestData();

    // Should find NAL unit.
    int result = H264Util.findNalUnit(data, 0, data.length, null);
    assertEquals(TEST_NAL_POSITION, result);
    // Should find NAL unit whose prefix ends one byte before the limit.
    result = H264Util.findNalUnit(data, 0, TEST_NAL_POSITION + 4, null);
    assertEquals(TEST_NAL_POSITION, result);
    // Shouldn't find NAL unit whose prefix ends at the limit (since the limit is exclusive).
    result = H264Util.findNalUnit(data, 0, TEST_NAL_POSITION + 3, null);
    assertEquals(TEST_NAL_POSITION + 3, result);
    // Should find NAL unit whose prefix starts at the offset.
    result = H264Util.findNalUnit(data, TEST_NAL_POSITION, data.length, null);
    assertEquals(TEST_NAL_POSITION, result);
    // Shouldn't find NAL unit whose prefix starts one byte past the offset.
    result = H264Util.findNalUnit(data, TEST_NAL_POSITION + 1, data.length, null);
    assertEquals(data.length, result);
  }

  public void testFindNalUnitWithPrefix() {
    byte[] data = buildTestData();

    // First byte of NAL unit in data1, rest in data2.
    boolean[] prefixFlags = new boolean[3];
    byte[] data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    byte[] data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, data.length);
    int result = H264Util.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertEquals(data1.length, result);
    result = H264Util.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertEquals(-1, result);
    assertPrefixFlagsCleared(prefixFlags);

    // First three bytes of NAL unit in data1, rest in data2.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 3);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 3, data.length);
    result = H264Util.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertEquals(data1.length, result);
    result = H264Util.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertEquals(-3, result);
    assertPrefixFlagsCleared(prefixFlags);

    // First byte of NAL unit in data1, second byte in data2, rest in data3.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, TEST_NAL_POSITION + 2);
    byte[] data3 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, data.length);
    result = H264Util.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertEquals(data1.length, result);
    result = H264Util.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertEquals(data2.length, result);
    result = H264Util.findNalUnit(data3, 0, data3.length, prefixFlags);
    assertEquals(-2, result);
    assertPrefixFlagsCleared(prefixFlags);

    // NAL unit split with one byte in four arrays.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, TEST_NAL_POSITION + 2);
    data3 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, TEST_NAL_POSITION + 3);
    byte[] data4 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, data.length);
    result = H264Util.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertEquals(data1.length, result);
    result = H264Util.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertEquals(data2.length, result);
    result = H264Util.findNalUnit(data3, 0, data3.length, prefixFlags);
    assertEquals(data3.length, result);
    result = H264Util.findNalUnit(data4, 0, data4.length, prefixFlags);
    assertEquals(-3, result);
    assertPrefixFlagsCleared(prefixFlags);

    // NAL unit entirely in data2. data1 ends with partial prefix.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_PARTIAL_NAL_POSITION + 2);
    data2 = Arrays.copyOfRange(data, TEST_PARTIAL_NAL_POSITION + 2, data.length);
    result = H264Util.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertEquals(data1.length, result);
    result = H264Util.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertEquals(4, result);
    assertPrefixFlagsCleared(prefixFlags);
  }

  private static byte[] buildTestData() {
    byte[] data = new byte[20];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) 0xFF;
    }
    // Insert an incomplete NAL unit start code.
    data[TEST_PARTIAL_NAL_POSITION] = 0;
    data[TEST_PARTIAL_NAL_POSITION + 1] = 0;
    // Insert a complete NAL unit start code.
    data[TEST_NAL_POSITION] = 0;
    data[TEST_NAL_POSITION + 1] = 0;
    data[TEST_NAL_POSITION + 2] = 1;
    data[TEST_NAL_POSITION + 3] = 5;
    return data;
  }

  private static void assertPrefixFlagsCleared(boolean[] flags) {
    assertEquals(false, flags[0] || flags[1] || flags[2]);
  }

}
