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
package com.google.android.exoplayer.upstream;

import com.google.android.exoplayer.testutil.Util;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;

/**
 * Unit tests for {@link DataSourceStream}.
 */
public class DataSourceStreamTest extends TestCase {

  private static final int DATA_LENGTH = 1024;
  private static final int BUFFER_LENGTH = 128;

  public void testGetLoadedData() throws IOException, InterruptedException {
    byte[] testData = Util.buildTestData(DATA_LENGTH);
    DataSource dataSource = new ByteArrayDataSource(testData);
    DataSpec dataSpec = new DataSpec(null, 0, DATA_LENGTH, null);
    DataSourceStream dataSourceStream = new DataSourceStream(dataSource, dataSpec,
        new BufferPool(BUFFER_LENGTH));

    dataSourceStream.load();
    // Assert that the read and load positions are correct.
    assertEquals(0, dataSourceStream.getReadPosition());
    assertEquals(testData.length, dataSourceStream.getLoadPosition());

    int halfTestDataLength = testData.length / 2;
    byte[] readData = new byte[testData.length];
    int bytesRead = dataSourceStream.read(readData, 0, halfTestDataLength);
    // Assert that the read position is updated correctly.
    assertEquals(halfTestDataLength, bytesRead);
    assertEquals(halfTestDataLength, dataSourceStream.getReadPosition());

    bytesRead += dataSourceStream.read(readData, bytesRead, testData.length - bytesRead);
    // Assert that the read position was updated correctly.
    assertEquals(testData.length, bytesRead);
    assertEquals(testData.length, dataSourceStream.getReadPosition());
    // Assert that the data read using the two read calls either side of getLoadedData is correct.
    assertTrue(Arrays.equals(testData, readData));
  }

}
