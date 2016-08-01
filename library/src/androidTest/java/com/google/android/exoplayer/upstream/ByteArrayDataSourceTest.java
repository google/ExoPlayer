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

import com.google.android.exoplayer.C;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * Unit tests for {@link ByteArrayDataSource}.
 */
public class ByteArrayDataSourceTest extends TestCase {

  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
  private static final byte[] TEST_DATA_ODD = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

  public void testFullReadSingleBytes() {
    readTestData(TEST_DATA, 0, C.LENGTH_UNBOUNDED, 1, 0, 1, false);
  }

  public void testFullReadAllBytes() {
    readTestData(TEST_DATA, 0, C.LENGTH_UNBOUNDED, 100, 0, 100, false);
  }

  public void testLimitReadSingleBytes() {
    // Limit set to the length of the data.
    readTestData(TEST_DATA, 0, TEST_DATA.length, 1, 0, 1, false);
    // And less.
    readTestData(TEST_DATA, 0, 6, 1, 0, 1, false);
  }

  public void testFullReadTwoBytes() {
    // Try with the total data length an exact multiple of the size of each individual read.
    readTestData(TEST_DATA, 0, C.LENGTH_UNBOUNDED, 2, 0, 2, false);
    // And not.
    readTestData(TEST_DATA_ODD, 0, C.LENGTH_UNBOUNDED, 2, 0, 2, false);
  }

  public void testLimitReadTwoBytes() {
    // Try with the limit an exact multiple of the size of each individual read.
    readTestData(TEST_DATA, 0, 6, 2, 0, 2, false);
    // And not.
    readTestData(TEST_DATA, 0, 7, 2, 0, 2, false);
  }

  public void testReadFromValidOffsets() {
    // Read from an offset without bound.
    readTestData(TEST_DATA, 1, C.LENGTH_UNBOUNDED, 1, 0, 1, false);
    // And with bound.
    readTestData(TEST_DATA, 1, 6, 1, 0, 1, false);
    // Read from the last possible offset without bound.
    readTestData(TEST_DATA, TEST_DATA.length - 1, C.LENGTH_UNBOUNDED, 1, 0, 1, false);
    // And with bound.
    readTestData(TEST_DATA, TEST_DATA.length - 1, 1, 1, 0, 1, false);
  }

  public void testReadFromInvalidOffsets() {
    // Read from first invalid offset and check failure without bound.
    readTestData(TEST_DATA, TEST_DATA.length, C.LENGTH_UNBOUNDED, 1, 0, 1, true);
    // And with bound.
    readTestData(TEST_DATA, TEST_DATA.length, 1, 1, 0, 1, true);
  }

  public void testReadWithInvalidLength() {
    // Read more data than is available.
    readTestData(TEST_DATA, 0, TEST_DATA.length + 1, 1, 0, 1, true);
    // And with bound.
    readTestData(TEST_DATA, 1, TEST_DATA.length, 1, 0, 1, true);
  }

  /**
   * Tests reading from a {@link ByteArrayDataSource} with various parameters.
   *
   * @param testData The data that the {@link ByteArrayDataSource} will wrap.
   * @param dataOffset The offset from which to read data.
   * @param dataLength The total length of data to read.
   * @param outputBufferLength The length of the target buffer for each read.
   * @param writeOffset The offset into {@code outputBufferLength} for each read.
   * @param maxReadLength The maximum length of each read.
   * @param expectFailOnOpen Whether it is expected that opening the source will fail.
   */
  private void readTestData(byte[] testData, int dataOffset, int dataLength, int outputBufferLength,
      int writeOffset, int maxReadLength, boolean expectFailOnOpen) {
    int expectedFinalBytesRead =
        dataLength == C.LENGTH_UNBOUNDED ? (testData.length - dataOffset) : dataLength;
    ByteArrayDataSource dataSource = new ByteArrayDataSource(testData);
    boolean opened = false;
    try {
      // Open the source.
      long length = dataSource.open(new DataSpec(null, dataOffset, dataLength, null));
      opened = true;
      assertFalse(expectFailOnOpen);

      // Verify the resolved length is as we expect.
      assertEquals(expectedFinalBytesRead, length);

      byte[] outputBuffer = new byte[outputBufferLength];
      int accumulatedBytesRead = 0;
      while (true) {
        // Calculate a valid length for the next read, constraining by the specified output buffer
        // length, write offset and maximum write length input parameters.
        int requestedReadLength = Math.min(maxReadLength, outputBufferLength - writeOffset);
        assertTrue(requestedReadLength > 0);

        int bytesRead = dataSource.read(outputBuffer, writeOffset, requestedReadLength);
        if (bytesRead != -1) {
          assertTrue(bytesRead > 0);
          assertTrue(bytesRead <= requestedReadLength);
          // Check the data read was correct.
          for (int i = 0; i < bytesRead; i++) {
            assertEquals(testData[dataOffset + accumulatedBytesRead + i],
                outputBuffer[writeOffset + i]);
          }
          // Check that we haven't read more data than we were expecting.
          accumulatedBytesRead += bytesRead;
          assertTrue(accumulatedBytesRead <= expectedFinalBytesRead);
          // If we haven't read all of the bytes the request should have been satisfied in full.
          assertTrue(accumulatedBytesRead == expectedFinalBytesRead
              || bytesRead == requestedReadLength);
        } else {
          // We're done. Check we read the expected number of bytes.
          assertEquals(expectedFinalBytesRead, accumulatedBytesRead);
          return;
        }
      }
    } catch (IOException e) {
      if (expectFailOnOpen && !opened) {
        // Expected.
        return;
      }
      // Unexpected failure.
      fail();
    }
  }

}
