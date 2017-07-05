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

import com.google.android.exoplayer2.testutil.TestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import junit.framework.TestCase;

/**
 * Unit tests for {@link Util}.
 */
public class UtilTest extends TestCase {

  public void testArrayBinarySearchFloor() {
    long[] values = new long[0];
    assertEquals(-1, Util.binarySearchFloor(values, 0, false, false));
    assertEquals(0, Util.binarySearchFloor(values, 0, false, true));

    values = new long[] {1, 3, 5};
    assertEquals(-1, Util.binarySearchFloor(values, 0, false, false));
    assertEquals(-1, Util.binarySearchFloor(values, 0, true, false));
    assertEquals(0, Util.binarySearchFloor(values, 0, false, true));
    assertEquals(0, Util.binarySearchFloor(values, 0, true, true));

    assertEquals(-1, Util.binarySearchFloor(values, 1, false, false));
    assertEquals(0, Util.binarySearchFloor(values, 1, true, false));
    assertEquals(0, Util.binarySearchFloor(values, 1, false, true));
    assertEquals(0, Util.binarySearchFloor(values, 1, true, true));

    assertEquals(1, Util.binarySearchFloor(values, 4, false, false));
    assertEquals(1, Util.binarySearchFloor(values, 4, true, false));

    assertEquals(1, Util.binarySearchFloor(values, 5, false, false));
    assertEquals(2, Util.binarySearchFloor(values, 5, true, false));

    assertEquals(2, Util.binarySearchFloor(values, 6, false, false));
    assertEquals(2, Util.binarySearchFloor(values, 6, true, false));
  }

  public void testListBinarySearchFloor() {
    List<Integer> values = new ArrayList<>();
    assertEquals(-1, Util.binarySearchFloor(values, 0, false, false));
    assertEquals(0, Util.binarySearchFloor(values, 0, false, true));

    values.add(1);
    values.add(3);
    values.add(5);
    assertEquals(-1, Util.binarySearchFloor(values, 0, false, false));
    assertEquals(-1, Util.binarySearchFloor(values, 0, true, false));
    assertEquals(0, Util.binarySearchFloor(values, 0, false, true));
    assertEquals(0, Util.binarySearchFloor(values, 0, true, true));

    assertEquals(-1, Util.binarySearchFloor(values, 1, false, false));
    assertEquals(0, Util.binarySearchFloor(values, 1, true, false));
    assertEquals(0, Util.binarySearchFloor(values, 1, false, true));
    assertEquals(0, Util.binarySearchFloor(values, 1, true, true));

    assertEquals(1, Util.binarySearchFloor(values, 4, false, false));
    assertEquals(1, Util.binarySearchFloor(values, 4, true, false));

    assertEquals(1, Util.binarySearchFloor(values, 5, false, false));
    assertEquals(2, Util.binarySearchFloor(values, 5, true, false));

    assertEquals(2, Util.binarySearchFloor(values, 6, false, false));
    assertEquals(2, Util.binarySearchFloor(values, 6, true, false));
  }

  public void testArrayBinarySearchCeil() {
    long[] values = new long[0];
    assertEquals(0, Util.binarySearchCeil(values, 0, false, false));
    assertEquals(-1, Util.binarySearchCeil(values, 0, false, true));

    values = new long[] {1, 3, 5};
    assertEquals(0, Util.binarySearchCeil(values, 0, false, false));
    assertEquals(0, Util.binarySearchCeil(values, 0, true, false));

    assertEquals(1, Util.binarySearchCeil(values, 1, false, false));
    assertEquals(0, Util.binarySearchCeil(values, 1, true, false));

    assertEquals(1, Util.binarySearchCeil(values, 2, false, false));
    assertEquals(1, Util.binarySearchCeil(values, 2, true, false));

    assertEquals(3, Util.binarySearchCeil(values, 5, false, false));
    assertEquals(2, Util.binarySearchCeil(values, 5, true, false));
    assertEquals(2, Util.binarySearchCeil(values, 5, false, true));
    assertEquals(2, Util.binarySearchCeil(values, 5, true, true));

    assertEquals(3, Util.binarySearchCeil(values, 6, false, false));
    assertEquals(3, Util.binarySearchCeil(values, 6, true, false));
    assertEquals(2, Util.binarySearchCeil(values, 6, false, true));
    assertEquals(2, Util.binarySearchCeil(values, 6, true, true));
  }

  public void testListBinarySearchCeil() {
    List<Integer> values = new ArrayList<>();
    assertEquals(0, Util.binarySearchCeil(values, 0, false, false));
    assertEquals(-1, Util.binarySearchCeil(values, 0, false, true));

    values.add(1);
    values.add(3);
    values.add(5);
    assertEquals(0, Util.binarySearchCeil(values, 0, false, false));
    assertEquals(0, Util.binarySearchCeil(values, 0, true, false));

    assertEquals(1, Util.binarySearchCeil(values, 1, false, false));
    assertEquals(0, Util.binarySearchCeil(values, 1, true, false));

    assertEquals(1, Util.binarySearchCeil(values, 2, false, false));
    assertEquals(1, Util.binarySearchCeil(values, 2, true, false));

    assertEquals(3, Util.binarySearchCeil(values, 5, false, false));
    assertEquals(2, Util.binarySearchCeil(values, 5, true, false));
    assertEquals(2, Util.binarySearchCeil(values, 5, false, true));
    assertEquals(2, Util.binarySearchCeil(values, 5, true, true));

    assertEquals(3, Util.binarySearchCeil(values, 6, false, false));
    assertEquals(3, Util.binarySearchCeil(values, 6, true, false));
    assertEquals(2, Util.binarySearchCeil(values, 6, false, true));
    assertEquals(2, Util.binarySearchCeil(values, 6, true, true));
  }

  public void testParseXsDuration() {
    assertEquals(150279L, Util.parseXsDuration("PT150.279S"));
    assertEquals(1500L, Util.parseXsDuration("PT1.500S"));
  }

  public void testParseXsDateTime() throws Exception {
    assertEquals(1403219262000L, Util.parseXsDateTime("2014-06-19T23:07:42"));
    assertEquals(1407322800000L, Util.parseXsDateTime("2014-08-06T11:00:00Z"));
    assertEquals(1407322800000L, Util.parseXsDateTime("2014-08-06T11:00:00,000Z"));
    assertEquals(1411161535000L, Util.parseXsDateTime("2014-09-19T13:18:55-08:00"));
    assertEquals(1411161535000L, Util.parseXsDateTime("2014-09-19T13:18:55-0800"));
    assertEquals(1411161535000L, Util.parseXsDateTime("2014-09-19T13:18:55.000-0800"));
    assertEquals(1411161535000L, Util.parseXsDateTime("2014-09-19T13:18:55.000-800"));
  }

  public void testUnescapeInvalidFileName() {
    assertNull(Util.unescapeFileName("%a"));
    assertNull(Util.unescapeFileName("%xyz"));
  }

  public void testEscapeUnescapeFileName() {
    assertEscapeUnescapeFileName("just+a regular+fileName", "just+a regular+fileName");
    assertEscapeUnescapeFileName("key:value", "key%3avalue");
    assertEscapeUnescapeFileName("<>:\"/\\|?*%", "%3c%3e%3a%22%2f%5c%7c%3f%2a%25");

    Random random = new Random(0);
    for (int i = 0; i < 1000; i++) {
      String string = TestUtil.buildTestString(1000, random);
      assertEscapeUnescapeFileName(string);
    }
  }

  private static void assertEscapeUnescapeFileName(String fileName, String escapedFileName) {
    assertEquals(escapedFileName, Util.escapeFileName(fileName));
    assertEquals(fileName, Util.unescapeFileName(escapedFileName));
  }

  private static void assertEscapeUnescapeFileName(String fileName) {
    String escapedFileName = Util.escapeFileName(fileName);
    assertEquals(fileName, Util.unescapeFileName(escapedFileName));
  }

}
