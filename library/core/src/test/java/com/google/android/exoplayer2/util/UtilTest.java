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

import static com.google.android.exoplayer2.util.Util.binarySearchCeil;
import static com.google.android.exoplayer2.util.Util.binarySearchFloor;
import static com.google.android.exoplayer2.util.Util.escapeFileName;
import static com.google.android.exoplayer2.util.Util.parseXsDateTime;
import static com.google.android.exoplayer2.util.Util.parseXsDuration;
import static com.google.android.exoplayer2.util.Util.unescapeFileName;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link Util}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public class UtilTest {

  @Test
  public void testInferContentType() {
    assertThat(Util.inferContentType("http://a.b/c.ism")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.ism/Manifest")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml/manifest")).isEqualTo(C.TYPE_SS);
    assertThat(Util.inferContentType("http://a.b/c.isml/manifest(filter=x)")).isEqualTo(C.TYPE_SS);

    assertThat(Util.inferContentType("http://a.b/c.ism/prefix-manifest")).isEqualTo(C.TYPE_OTHER);
    assertThat(Util.inferContentType("http://a.b/c.ism/manifest-suffix")).isEqualTo(C.TYPE_OTHER);
  }

  @Test
  public void testArrayBinarySearchFloor() {
    long[] values = new long[0];
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);

    values = new long[] {1, 3, 5};
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, true, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 0, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 1, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 1, true, false)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 4, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 4, true, false)).isEqualTo(1);

    assertThat(binarySearchFloor(values, 5, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 5, true, false)).isEqualTo(2);

    assertThat(binarySearchFloor(values, 6, false, false)).isEqualTo(2);
    assertThat(binarySearchFloor(values, 6, true, false)).isEqualTo(2);
  }

  @Test
  public void testListBinarySearchFloor() {
    List<Integer> values = new ArrayList<>();
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);

    values.add(1);
    values.add(3);
    values.add(5);
    assertThat(binarySearchFloor(values, 0, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, true, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 0, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 0, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 1, false, false)).isEqualTo(-1);
    assertThat(binarySearchFloor(values, 1, true, false)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, false, true)).isEqualTo(0);
    assertThat(binarySearchFloor(values, 1, true, true)).isEqualTo(0);

    assertThat(binarySearchFloor(values, 4, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 4, true, false)).isEqualTo(1);

    assertThat(binarySearchFloor(values, 5, false, false)).isEqualTo(1);
    assertThat(binarySearchFloor(values, 5, true, false)).isEqualTo(2);

    assertThat(binarySearchFloor(values, 6, false, false)).isEqualTo(2);
    assertThat(binarySearchFloor(values, 6, true, false)).isEqualTo(2);
  }

  @Test
  public void testArrayBinarySearchCeil() {
    long[] values = new long[0];
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, false, true)).isEqualTo(-1);

    values = new long[] {1, 3, 5};
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 1, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 1, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 2, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 2, true, false)).isEqualTo(1);

    assertThat(binarySearchCeil(values, 5, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 5, true, false)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, true, true)).isEqualTo(2);

    assertThat(binarySearchCeil(values, 6, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, true, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 6, true, true)).isEqualTo(2);
  }

  @Test
  public void testListBinarySearchCeil() {
    List<Integer> values = new ArrayList<>();
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, false, true)).isEqualTo(-1);

    values.add(1);
    values.add(3);
    values.add(5);
    assertThat(binarySearchCeil(values, 0, false, false)).isEqualTo(0);
    assertThat(binarySearchCeil(values, 0, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 1, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 1, true, false)).isEqualTo(0);

    assertThat(binarySearchCeil(values, 2, false, false)).isEqualTo(1);
    assertThat(binarySearchCeil(values, 2, true, false)).isEqualTo(1);

    assertThat(binarySearchCeil(values, 5, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 5, true, false)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 5, true, true)).isEqualTo(2);

    assertThat(binarySearchCeil(values, 6, false, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, true, false)).isEqualTo(3);
    assertThat(binarySearchCeil(values, 6, false, true)).isEqualTo(2);
    assertThat(binarySearchCeil(values, 6, true, true)).isEqualTo(2);
  }

  @Test
  public void testParseXsDuration() {
    assertThat(parseXsDuration("PT150.279S")).isEqualTo(150279L);
    assertThat(parseXsDuration("PT1.500S")).isEqualTo(1500L);
  }

  @Test
  public void testParseXsDateTime() throws Exception {
    assertThat(parseXsDateTime("2014-06-19T23:07:42")).isEqualTo(1403219262000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-08-06T11:00:00,000Z")).isEqualTo(1407322800000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-08:00")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-0800")).isEqualTo(1411161535000L);
    assertThat(parseXsDateTime("2014-09-19T13:18:55.000-800")).isEqualTo(1411161535000L);
  }

  @Test
  public void testUnescapeInvalidFileName() {
    assertThat(Util.unescapeFileName("%a")).isNull();
    assertThat(Util.unescapeFileName("%xyz")).isNull();
  }

  @Test
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
    assertThat(escapeFileName(fileName)).isEqualTo(escapedFileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

  private static void assertEscapeUnescapeFileName(String fileName) {
    String escapedFileName = Util.escapeFileName(fileName);
    assertThat(unescapeFileName(escapedFileName)).isEqualTo(fileName);
  }

}
