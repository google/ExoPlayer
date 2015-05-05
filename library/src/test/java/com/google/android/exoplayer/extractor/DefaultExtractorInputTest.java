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
package com.google.android.exoplayer.extractor;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.testutil.FakeDataSource;
import com.google.android.exoplayer.upstream.DataSpec;

import android.net.Uri;

import junit.framework.TestCase;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * Test for {@link DefaultExtractorInput}.
 */
public class DefaultExtractorInputTest extends TestCase {

  private static final String TEST_URI = "http://www.google.com";
  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};

  public void testInitialPosition() throws IOException {
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input =
        new DefaultExtractorInput(testDataSource, 123, C.LENGTH_UNBOUNDED);
    assertEquals(123, input.getPosition());
  }

  public void testRead() throws IOException, InterruptedException {
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    byte[] target = new byte[TEST_DATA.length];
    // We expect to perform three reads of three bytes, as setup in buildTestDataSource.
    int bytesRead = 0;
    bytesRead += input.read(target, 0, TEST_DATA.length);
    assertEquals(3, bytesRead);
    bytesRead += input.read(target, 3, TEST_DATA.length);
    assertEquals(6, bytesRead);
    bytesRead += input.read(target, 6, TEST_DATA.length);
    assertEquals(9, bytesRead);
    // Check the read data is correct.
    assertTrue(Arrays.equals(TEST_DATA, target));
    // Check we're now indicated that the end of input is reached.
    int expectedEndOfInput = input.read(target, 0, TEST_DATA.length);
    assertEquals(-1, expectedEndOfInput);
  }

  public void testReadFullyOnce() throws IOException, InterruptedException {
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    byte[] target = new byte[TEST_DATA.length];
    input.readFully(target, 0, TEST_DATA.length);
    // Check that we read the whole of TEST_DATA.
    assertTrue(Arrays.equals(TEST_DATA, target));
    assertEquals(TEST_DATA.length, input.getPosition());
    // Check that we see end of input if we read again with allowEndOfInput set.
    boolean result = input.readFully(target, 0, 1, true);
    assertFalse(result);
    // Check that we fail with EOFException we read again with allowEndOfInput unset.
    try {
      input.readFully(target, 0, 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  public void testReadFullyTwice() throws IOException, InterruptedException {
    // Read TEST_DATA in two parts.
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    byte[] target = new byte[5];
    input.readFully(target, 0, 5);
    assertTrue(Arrays.equals(Arrays.copyOf(TEST_DATA, 5), target));
    assertEquals(5, input.getPosition());
    target = new byte[4];
    input.readFully(target, 0, 4);
    assertTrue(Arrays.equals(Arrays.copyOfRange(TEST_DATA, 5, 9), target));
    assertEquals(5 + 4, input.getPosition());
  }

  public void testReadFullyTooMuch() throws IOException, InterruptedException {
    // Read more than TEST_DATA. Should fail with an EOFException. Position should not update.
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    try {
      byte[] target = new byte[TEST_DATA.length + 1];
      input.readFully(target, 0, TEST_DATA.length + 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertEquals(0, input.getPosition());

    // Read more than TEST_DATA with allowEndOfInput set. Should fail with an EOFException because
    // the end of input isn't encountered immediately. Position should not update.
    testDataSource = buildDataSource();
    input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    try {
      byte[] target = new byte[TEST_DATA.length + 1];
      input.readFully(target, 0, TEST_DATA.length + 1, true);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertEquals(0, input.getPosition());
  }

  public void testReadFullyWithFailingDataSource() throws IOException, InterruptedException {
    FakeDataSource testDataSource = buildFailingDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    try {
      byte[] target = new byte[TEST_DATA.length];
      input.readFully(target, 0, TEST_DATA.length);
      fail();
    } catch (IOException e) {
      // Expected.
    }
    // The position should not have advanced.
    assertEquals(0, input.getPosition());
  }

  public void testSkipFullyOnce() throws IOException, InterruptedException {
    // Skip TEST_DATA.
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    input.skipFully(TEST_DATA.length);
    assertEquals(TEST_DATA.length, input.getPosition());
    // Check that we fail with EOFException we skip again.
    try {
      input.skipFully(1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  public void testSkipFullyTwice() throws IOException, InterruptedException {
    // Skip TEST_DATA in two parts.
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    input.skipFully(5);
    assertEquals(5, input.getPosition());
    input.skipFully(4);
    assertEquals(5 + 4, input.getPosition());
  }

  public void testSkipFullyTooMuch() throws IOException, InterruptedException {
    // Skip more than TEST_DATA. Should fail with an EOFException. Position should not update.
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    try {
      input.skipFully(TEST_DATA.length + 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertEquals(0, input.getPosition());
  }

  public void testSkipFullyWithFailingDataSource() throws IOException, InterruptedException {
    FakeDataSource testDataSource = buildFailingDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    try {
      input.skipFully(TEST_DATA.length);
      fail();
    } catch (IOException e) {
      // Expected.
    }
    // The position should not have advanced.
    assertEquals(0, input.getPosition());
  }

  public void testSkipFullyLarge() throws IOException, InterruptedException {
    // Tests skipping an amount of data that's larger than any internal scratch space.
    int largeSkipSize = 1024 * 1024;
    FakeDataSource.Builder builder = new FakeDataSource.Builder();
    builder.appendReadData(new byte[largeSkipSize]);
    FakeDataSource testDataSource = builder.build();
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));

    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNBOUNDED);
    input.skipFully(largeSkipSize);
    assertEquals(largeSkipSize, input.getPosition());
    // Check that we fail with EOFException we skip again.
    try {
      input.skipFully(1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  private static FakeDataSource buildDataSource() throws IOException {
    FakeDataSource.Builder builder = new FakeDataSource.Builder();
    builder.appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 3));
    builder.appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6));
    builder.appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    FakeDataSource testDataSource = builder.build();
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    return testDataSource;
  }

  private static FakeDataSource buildFailingDataSource() throws IOException {
    FakeDataSource.Builder builder = new FakeDataSource.Builder();
    builder.appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 6));
    builder.appendReadError(new IOException());
    builder.appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    FakeDataSource testDataSource = builder.build();
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    return testDataSource;
  }

}
