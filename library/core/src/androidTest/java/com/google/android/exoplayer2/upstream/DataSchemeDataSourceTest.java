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
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit tests for {@link DataSchemeDataSource}.
 */
public final class DataSchemeDataSourceTest extends TestCase {

  private DataSource schemeDataDataSource;

  @Override
  public void setUp() {
    schemeDataDataSource = new DataSchemeDataSource();
  }

  public void testBase64Data() throws IOException {
    DataSpec dataSpec = buildDataSpec("data:text/plain;base64,eyJwcm92aWRlciI6IndpZGV2aW5lX3Rlc3QiL"
        + "CJjb250ZW50X2lkIjoiTWpBeE5WOTBaV0Z5Y3c9PSIsImtleV9pZHMiOlsiMDAwMDAwMDAwMDAwMDAwMDAwMDAwM"
        + "DAwMDAwMDAwMDAiXX0=");
    TestUtil.assertDataSourceContent(schemeDataDataSource, dataSpec,
        ("{\"provider\":\"widevine_test\",\"content_id\":\"MjAxNV90ZWFycw==\",\"key_ids\":"
        + "[\"00000000000000000000000000000000\"]}").getBytes());
  }

  public void testAsciiData() throws IOException {
    TestUtil.assertDataSourceContent(schemeDataDataSource, buildDataSpec("data:,A%20brief%20note"),
        "A brief note".getBytes());
  }

  public void testPartialReads() throws IOException {
    byte[] buffer = new byte[18];
    DataSpec dataSpec = buildDataSpec("data:,012345678901234567");
    assertEquals(18, schemeDataDataSource.open(dataSpec));
    assertEquals(9, schemeDataDataSource.read(buffer, 0, 9));
    assertEquals(0, schemeDataDataSource.read(buffer, 3, 0));
    assertEquals(9, schemeDataDataSource.read(buffer, 9, 15));
    assertEquals(0, schemeDataDataSource.read(buffer, 1, 0));
    assertEquals(C.RESULT_END_OF_INPUT, schemeDataDataSource.read(buffer, 1, 1));
    assertEquals("012345678901234567", new String(buffer, 0, 18));
  }

  public void testIncorrectScheme() {
    try {
      schemeDataDataSource.open(buildDataSpec("http://www.google.com"));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  public void testMalformedData() {
    try {
      schemeDataDataSource.open(buildDataSpec("data:text/plain;base64,,This%20is%20Content"));
      fail();
    } catch (IOException e) {
      // Expected.
    }
    try {
      schemeDataDataSource.open(buildDataSpec("data:text/plain;base64,IncorrectPadding=="));
      fail();
    } catch (IOException e) {
      // Expected.
    }
  }

  private static DataSpec buildDataSpec(String uriString) {
    return new DataSpec(Uri.parse(uriString));
  }

}
