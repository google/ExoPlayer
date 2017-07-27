/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.upstream;

import android.net.Uri;
import android.test.MoreAsserts;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import java.io.IOException;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * Unit tests for {@link DataSourceInputStream}.
 */
public class DataSourceInputStreamTest extends TestCase {

  private static final byte[] TEST_DATA = TestUtil.buildTestData(16);

  public void testReadSingleBytes() throws IOException {
    DataSourceInputStream inputStream = buildTestInputStream();
    // No bytes read yet.
    assertEquals(0, inputStream.bytesRead());
    // Read bytes.
    for (int i = 0; i < TEST_DATA.length; i++) {
      int readByte = inputStream.read();
      assertTrue(0 <= readByte && readByte < 256);
      assertEquals(TEST_DATA[i] & 0xFF, readByte);
      assertEquals(i + 1, inputStream.bytesRead());
    }
    // Check end of stream.
    assertEquals(-1, inputStream.read());
    assertEquals(TEST_DATA.length, inputStream.bytesRead());
    // Check close succeeds.
    inputStream.close();
  }

  public void testRead() throws IOException {
    DataSourceInputStream inputStream = buildTestInputStream();
    // Read bytes.
    byte[] readBytes = new byte[TEST_DATA.length];
    int totalBytesRead = 0;
    while (totalBytesRead < TEST_DATA.length) {
      long bytesRead = inputStream.read(readBytes, totalBytesRead,
          TEST_DATA.length - totalBytesRead);
      assertTrue(bytesRead > 0);
      totalBytesRead += bytesRead;
      assertEquals(totalBytesRead, inputStream.bytesRead());
    }
    // Check the read data.
    MoreAsserts.assertEquals(TEST_DATA, readBytes);
    // Check end of stream.
    assertEquals(TEST_DATA.length, inputStream.bytesRead());
    assertEquals(TEST_DATA.length, totalBytesRead);
    assertEquals(-1, inputStream.read());
    // Check close succeeds.
    inputStream.close();
  }

  public void testSkip() throws IOException {
    DataSourceInputStream inputStream = buildTestInputStream();
    // Skip bytes.
    long totalBytesSkipped = 0;
    while (totalBytesSkipped < TEST_DATA.length) {
      long bytesSkipped = inputStream.skip(Long.MAX_VALUE);
      assertTrue(bytesSkipped > 0);
      totalBytesSkipped += bytesSkipped;
      assertEquals(totalBytesSkipped, inputStream.bytesRead());
    }
    // Check end of stream.
    assertEquals(TEST_DATA.length, inputStream.bytesRead());
    assertEquals(TEST_DATA.length, totalBytesSkipped);
    assertEquals(-1, inputStream.read());
    // Check close succeeds.
    inputStream.close();
  }

  private static DataSourceInputStream buildTestInputStream() {
    FakeDataSource fakeDataSource = new FakeDataSource();
    fakeDataSource.getDataSet().newDefaultData()
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 5))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 5, 10))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 10, 15))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 15, TEST_DATA.length));
    return new DataSourceInputStream(fakeDataSource, new DataSpec(Uri.EMPTY));
  }

}
