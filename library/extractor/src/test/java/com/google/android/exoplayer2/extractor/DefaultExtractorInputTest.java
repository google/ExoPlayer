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
package com.google.android.exoplayer2.extractor;

import static com.google.android.exoplayer2.C.RESULT_END_OF_INPUT;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.copyOf;
import static java.util.Arrays.copyOfRange;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link DefaultExtractorInput}. */
@RunWith(AndroidJUnit4.class)
public class DefaultExtractorInputTest {

  private static final String TEST_URI = "http://www.google.com";
  private static final byte[] TEST_DATA = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
  private static final int LARGE_TEST_DATA_LENGTH = 8192;

  @Test
  public void initialPosition() throws Exception {
    FakeDataSource testDataSource = buildDataSource();
    DefaultExtractorInput input =
        new DefaultExtractorInput(testDataSource, 123, C.LENGTH_UNSET);
    assertThat(input.getPosition()).isEqualTo(123);
  }

  @Test
  public void readMultipleTimes() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];
    // We expect to perform three reads of three bytes, as setup in buildTestDataSource.
    int bytesRead = 0;
    bytesRead += input.read(target, 0, TEST_DATA.length);
    assertThat(bytesRead).isEqualTo(3);
    bytesRead += input.read(target, 3, TEST_DATA.length);
    assertThat(bytesRead).isEqualTo(6);
    bytesRead += input.read(target, 6, TEST_DATA.length);
    assertThat(bytesRead).isEqualTo(9);
    assertThat(input.getPosition()).isEqualTo(9);
    assertThat(TEST_DATA).isEqualTo(target);
  }

  @Test
  public void readAlreadyPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(TEST_DATA.length);
    int bytesRead = input.read(target, 0, TEST_DATA.length - 1);

    assertThat(bytesRead).isEqualTo(TEST_DATA.length - 1);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length - 1);
    assertThat(Arrays.copyOf(TEST_DATA, TEST_DATA.length - 1))
        .isEqualTo(Arrays.copyOf(target, TEST_DATA.length - 1));
  }

  @Test
  public void readPartiallyPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(TEST_DATA.length - 1);
    int bytesRead = input.read(target, 0, TEST_DATA.length);

    assertThat(bytesRead).isEqualTo(TEST_DATA.length - 1);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length - 1);
    assertThat(Arrays.copyOf(TEST_DATA, TEST_DATA.length - 1))
        .isEqualTo(Arrays.copyOf(target, TEST_DATA.length - 1));
  }

  @Test
  public void readEndOfInputBeforeFirstByteRead() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.skipFully(TEST_DATA.length);
    int bytesRead = input.read(target, 0, TEST_DATA.length);

    assertThat(bytesRead).isEqualTo(RESULT_END_OF_INPUT);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void readEndOfInputAfterFirstByteRead() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.skipFully(TEST_DATA.length - 1);
    int bytesRead = input.read(target, 0, TEST_DATA.length);

    assertThat(bytesRead).isEqualTo(1);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void readZeroLength() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    int bytesRead = input.read(target, /* offset= */ 0, /* length= */ 0);

    assertThat(bytesRead).isEqualTo(0);
  }

  @Test
  public void readFullyOnce() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];
    input.readFully(target, 0, TEST_DATA.length);
    // Check that we read the whole of TEST_DATA.
    assertThat(TEST_DATA).isEqualTo(target);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
    // Check that we see end of input if we read again with allowEndOfInput set.
    boolean result = input.readFully(target, 0, 1, true);
    assertThat(result).isFalse();
    // Check that we fail with EOFException we read again with allowEndOfInput unset.
    try {
      input.readFully(target, 0, 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void readFullyTwice() throws Exception {
    // Read TEST_DATA in two parts.
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[5];
    input.readFully(target, 0, 5);
    assertThat(copyOf(TEST_DATA, 5)).isEqualTo(target);
    assertThat(input.getPosition()).isEqualTo(5);
    target = new byte[4];
    input.readFully(target, 0, 4);
    assertThat(copyOfRange(TEST_DATA, 5, 9)).isEqualTo(target);
    assertThat(input.getPosition()).isEqualTo(5 + 4);
  }

  @Test
  public void readFullyTooMuch() throws Exception {
    // Read more than TEST_DATA. Should fail with an EOFException. Position should not update.
    DefaultExtractorInput input = createDefaultExtractorInput();
    try {
      byte[] target = new byte[TEST_DATA.length + 1];
      input.readFully(target, 0, TEST_DATA.length + 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertThat(input.getPosition()).isEqualTo(0);

    // Read more than TEST_DATA with allowEndOfInput set. Should fail with an EOFException because
    // the end of input isn't encountered immediately. Position should not update.
    input = createDefaultExtractorInput();
    try {
      byte[] target = new byte[TEST_DATA.length + 1];
      input.readFully(target, 0, TEST_DATA.length + 1, true);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertThat(input.getPosition()).isEqualTo(0);
  }

  @Test
  public void readFullyWithFailingDataSource() throws Exception {
    FakeDataSource testDataSource = buildFailingDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    try {
      byte[] target = new byte[TEST_DATA.length];
      input.readFully(target, 0, TEST_DATA.length);
      fail();
    } catch (IOException e) {
      // Expected.
    }
    // The position should not have advanced.
    assertThat(input.getPosition()).isEqualTo(0);
  }

  @Test
  public void readFullyHalfPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(4);

    input.readFully(target, 0, TEST_DATA.length);

    // Check the read data is correct.
    assertThat(TEST_DATA).isEqualTo(target);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void skipMultipleTimes() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    // We expect to perform three skips of three bytes, as setup in buildTestDataSource.
    for (int i = 0; i < 3; i++) {
      assertThat(input.skip(TEST_DATA.length)).isEqualTo(3);
    }
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void largeSkip() throws Exception {
    FakeDataSource testDataSource = buildLargeDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    // Check that skipping the entire data source succeeds.
    int bytesToSkip = LARGE_TEST_DATA_LENGTH;
    while (bytesToSkip > 0) {
      int skipped = input.skip(bytesToSkip);
      assertThat(skipped).isGreaterThan(0);
      bytesToSkip -= skipped;
    }
    assertThat(bytesToSkip).isEqualTo(0);
  }

  @Test
  public void skipAlreadyPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();

    input.advancePeekPosition(TEST_DATA.length);
    int bytesSkipped = input.skip(TEST_DATA.length - 1);

    assertThat(bytesSkipped).isEqualTo(TEST_DATA.length - 1);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length - 1);
  }

  @Test
  public void skipPartiallyPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();

    input.advancePeekPosition(TEST_DATA.length - 1);
    int bytesSkipped = input.skip(TEST_DATA.length);

    assertThat(bytesSkipped).isEqualTo(TEST_DATA.length - 1);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length - 1);
  }

  @Test
  public void skipEndOfInputBeforeFirstByteSkipped() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();

    input.skipFully(TEST_DATA.length);
    int bytesSkipped = input.skip(TEST_DATA.length);

    assertThat(bytesSkipped).isEqualTo(RESULT_END_OF_INPUT);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void skipEndOfInputAfterFirstByteSkipped() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();

    input.skipFully(TEST_DATA.length - 1);
    int bytesSkipped = input.skip(TEST_DATA.length);

    assertThat(bytesSkipped).isEqualTo(1);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void skipZeroLength() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();

    int bytesRead = input.skip(0);

    assertThat(bytesRead).isEqualTo(0);
  }

  @Test
  public void skipFullyOnce() throws Exception {
    // Skip TEST_DATA.
    DefaultExtractorInput input = createDefaultExtractorInput();
    input.skipFully(TEST_DATA.length);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
    // Check that we see end of input if we skip again with allowEndOfInput set.
    boolean result = input.skipFully(1, true);
    assertThat(result).isFalse();
    // Check that we fail with EOFException we skip again.
    try {
      input.skipFully(1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void skipFullyTwice() throws Exception {
    // Skip TEST_DATA in two parts.
    DefaultExtractorInput input = createDefaultExtractorInput();
    input.skipFully(5);
    assertThat(input.getPosition()).isEqualTo(5);
    input.skipFully(4);
    assertThat(input.getPosition()).isEqualTo(5 + 4);
  }

  @Test
  public void skipFullyTwicePeeked() throws Exception {
    // Skip TEST_DATA.
    DefaultExtractorInput input = createDefaultExtractorInput();

    input.advancePeekPosition(TEST_DATA.length);

    int halfLength = TEST_DATA.length / 2;
    input.skipFully(halfLength);
    assertThat(input.getPosition()).isEqualTo(halfLength);

    input.skipFully(TEST_DATA.length - halfLength);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void skipFullyTooMuch() throws Exception {
    // Skip more than TEST_DATA. Should fail with an EOFException. Position should not update.
    DefaultExtractorInput input = createDefaultExtractorInput();
    try {
      input.skipFully(TEST_DATA.length + 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertThat(input.getPosition()).isEqualTo(0);

    // Skip more than TEST_DATA with allowEndOfInput set. Should fail with an EOFException because
    // the end of input isn't encountered immediately. Position should not update.
    input = createDefaultExtractorInput();
    try {
      input.skipFully(TEST_DATA.length + 1, true);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
    assertThat(input.getPosition()).isEqualTo(0);
  }

  @Test
  public void skipFullyWithFailingDataSource() throws Exception {
    FakeDataSource testDataSource = buildFailingDataSource();
    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    try {
      input.skipFully(TEST_DATA.length);
      fail();
    } catch (IOException e) {
      // Expected.
    }
    // The position should not have advanced.
    assertThat(input.getPosition()).isEqualTo(0);
  }

  @Test
  public void skipFullyLarge() throws Exception {
    // Tests skipping an amount of data that's larger than any internal scratch space.
    int largeSkipSize = 1024 * 1024;
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData().appendReadData(new byte[largeSkipSize]);
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));

    DefaultExtractorInput input = new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
    input.skipFully(largeSkipSize);
    assertThat(input.getPosition()).isEqualTo(largeSkipSize);
    // Check that we fail with EOFException we skip again.
    try {
      input.skipFully(1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void peekMultipleTimes() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    // We expect to perform three peeks of three bytes, as setup in buildTestDataSource.
    int bytesPeeked = 0;
    bytesPeeked += input.peek(target, 0, TEST_DATA.length);
    assertThat(bytesPeeked).isEqualTo(3);
    bytesPeeked += input.peek(target, 3, TEST_DATA.length);
    assertThat(bytesPeeked).isEqualTo(6);
    bytesPeeked += input.peek(target, 6, TEST_DATA.length);
    assertThat(bytesPeeked).isEqualTo(9);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);
    assertThat(TEST_DATA).isEqualTo(target);
  }

  @Test
  public void peekAlreadyPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(TEST_DATA.length);
    input.resetPeekPosition();
    int bytesPeeked = input.peek(target, 0, TEST_DATA.length - 1);

    assertThat(bytesPeeked).isEqualTo(TEST_DATA.length - 1);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length - 1);
    assertThat(Arrays.copyOf(TEST_DATA, TEST_DATA.length - 1))
        .isEqualTo(Arrays.copyOf(target, TEST_DATA.length - 1));
  }

  @Test
  public void peekPartiallyPeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(TEST_DATA.length - 1);
    input.resetPeekPosition();
    int bytesPeeked = input.peek(target, 0, TEST_DATA.length);

    assertThat(bytesPeeked).isEqualTo(TEST_DATA.length - 1);
    assertThat(Arrays.copyOf(TEST_DATA, TEST_DATA.length - 1))
        .isEqualTo(Arrays.copyOf(target, TEST_DATA.length - 1));
  }

  @Test
  public void peekEndOfInputBeforeFirstBytePeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(TEST_DATA.length);
    int bytesPeeked = input.peek(target, 0, TEST_DATA.length);

    assertThat(bytesPeeked).isEqualTo(RESULT_END_OF_INPUT);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void peekEndOfInputAfterFirstBytePeeked() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    input.advancePeekPosition(TEST_DATA.length - 1);
    int bytesPeeked = input.peek(target, 0, TEST_DATA.length);

    assertThat(bytesPeeked).isEqualTo(1);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);
  }

  @Test
  public void peekZeroLength() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    int bytesPeeked = input.peek(target, /* offset= */ 0, /* length= */ 0);

    assertThat(bytesPeeked).isEqualTo(0);
  }

  @Test
  public void peekFully() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];
    input.peekFully(target, 0, TEST_DATA.length);

    // Check that we read the whole of TEST_DATA.
    assertThat(TEST_DATA).isEqualTo(target);
    assertThat(input.getPosition()).isEqualTo(0);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);

    // Check that we can read again from the buffer
    byte[] target2 = new byte[TEST_DATA.length];
    input.readFully(target2, 0, TEST_DATA.length);
    assertThat(TEST_DATA).isEqualTo(target2);
    assertThat(input.getPosition()).isEqualTo(TEST_DATA.length);
    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);

    // Check that we fail with EOFException if we peek again
    try {
      input.peekFully(target, 0, 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void peekFullyAfterEofExceptionPeeksAsExpected() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length + 10];

    try {
      input.peekFully(target, /* offset= */ 0, target.length);
      fail();
    } catch (EOFException expected) {
      // Do nothing. Expected.
    }
    input.peekFully(target, /* offset= */ 0, /* length= */ TEST_DATA.length);

    assertThat(input.getPeekPosition()).isEqualTo(TEST_DATA.length);
    assertThat(TEST_DATA).isEqualTo(Arrays.copyOf(target, TEST_DATA.length));
  }

  @Test
  public void resetPeekPosition() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];
    input.peekFully(target, 0, TEST_DATA.length);

    // Check that we read the whole of TEST_DATA.
    assertThat(TEST_DATA).isEqualTo(target);
    assertThat(input.getPosition()).isEqualTo(0);

    // Check that we can peek again after resetting.
    input.resetPeekPosition();
    byte[] target2 = new byte[TEST_DATA.length];
    input.peekFully(target2, 0, TEST_DATA.length);
    assertThat(TEST_DATA).isEqualTo(target2);

    // Check that we fail with EOFException if we peek past the end of the input.
    try {
      input.peekFully(target, 0, 1);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void peekFullyAtEndOfStreamWithAllowEndOfInputSucceeds() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    // Check peeking up to the end of input succeeds.
    assertThat(input.peekFully(target, 0, TEST_DATA.length, true)).isTrue();

    // Check peeking at the end of input with allowEndOfInput signals the end of input.
    assertThat(input.peekFully(target, 0, 1, true)).isFalse();
  }

  @Test
  public void peekFullyAtEndThenReadEndOfInput() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    // Peek up to the end of the input.
    assertThat(input.peekFully(target, 0, TEST_DATA.length, false)).isTrue();

    // Peek the end of the input.
    assertThat(input.peekFully(target, 0, 1, true)).isFalse();

    // Read up to the end of the input.
    assertThat(input.readFully(target, 0, TEST_DATA.length, false)).isTrue();

    // Read the end of the input.
    assertThat(input.readFully(target, 0, 1, true)).isFalse();
  }

  @Test
  public void peekFullyAcrossEndOfInputWithAllowEndOfInputFails() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    // Check peeking before the end of input with allowEndOfInput succeeds.
    assertThat(input.peekFully(target, 0, TEST_DATA.length - 1, true)).isTrue();

    // Check peeking across the end of input with allowEndOfInput throws.
    try {
      input.peekFully(target, 0, 2, true);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  @Test
  public void resetAndPeekFullyPastEndOfStreamWithAllowEndOfInputFails() throws Exception {
    DefaultExtractorInput input = createDefaultExtractorInput();
    byte[] target = new byte[TEST_DATA.length];

    // Check peeking up to the end of input succeeds.
    assertThat(input.peekFully(target, 0, TEST_DATA.length, true)).isTrue();
    input.resetPeekPosition();
    try {
      // Check peeking one more byte throws.
      input.peekFully(target, 0, TEST_DATA.length + 1, true);
      fail();
    } catch (EOFException e) {
      // Expected.
    }
  }

  private static FakeDataSource buildDataSource() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData()
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 3))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 3, 6))
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    return testDataSource;
  }

  private static FakeDataSource buildLargeDataSource() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData().appendReadData(new byte[LARGE_TEST_DATA_LENGTH]);
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    return testDataSource;
  }

  private static FakeDataSource buildFailingDataSource() throws Exception {
    FakeDataSource testDataSource = new FakeDataSource();
    testDataSource.getDataSet().newDefaultData()
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 0, 6))
        .appendReadError(new IOException())
        .appendReadData(Arrays.copyOfRange(TEST_DATA, 6, 9));
    testDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    return testDataSource;
  }

  private static DefaultExtractorInput createDefaultExtractorInput() throws Exception {
    FakeDataSource testDataSource = buildDataSource();
    return new DefaultExtractorInput(testDataSource, 0, C.LENGTH_UNSET);
  }

}
