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
package com.google.android.exoplayer2.source;

import static com.google.android.exoplayer2.C.RESULT_BUFFER_READ;
import static com.google.android.exoplayer2.C.RESULT_FORMAT_READ;
import static com.google.android.exoplayer2.C.RESULT_NOTHING_READ;
import static com.google.android.exoplayer2.source.SampleQueue.ADVANCE_FAILED;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Long.MIN_VALUE;
import static java.util.Arrays.copyOfRange;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Test for {@link SampleQueue}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class SampleQueueTest {

  private static final int ALLOCATION_SIZE = 16;

  private static final Format TEST_FORMAT_1 = Format.createSampleFormat("1", "mimeType", 0);
  private static final Format TEST_FORMAT_2 = Format.createSampleFormat("2", "mimeType", 0);
  private static final Format TEST_FORMAT_1_COPY = Format.createSampleFormat("1", "mimeType", 0);
  private static final byte[] TEST_DATA = TestUtil.buildTestData(ALLOCATION_SIZE * 10);

  /*
   * TEST_SAMPLE_SIZES and TEST_SAMPLE_OFFSETS are intended to test various boundary cases (with
   * respect to the allocation size). TEST_SAMPLE_OFFSETS values are defined as the backward offsets
   * (as expected by SampleQueue.sampleMetadata) assuming that TEST_DATA has been written to the
   * sampleQueue in full. The allocations are filled as follows, where | indicates a boundary
   * between allocations and x indicates a byte that doesn't belong to a sample:
   *
   * x<s1>|x<s2>x|x<s3>|<s4>x|<s5>|<s6|s6>|x<s7|s7>x|<s8>
   */
  private static final int[] TEST_SAMPLE_SIZES = new int[] {
      ALLOCATION_SIZE - 1, ALLOCATION_SIZE - 2, ALLOCATION_SIZE - 1, ALLOCATION_SIZE - 1,
      ALLOCATION_SIZE, ALLOCATION_SIZE * 2, ALLOCATION_SIZE * 2 - 2, ALLOCATION_SIZE
  };
  private static final int[] TEST_SAMPLE_OFFSETS = new int[] {
      ALLOCATION_SIZE * 9, ALLOCATION_SIZE * 8 + 1, ALLOCATION_SIZE * 7, ALLOCATION_SIZE * 6 + 1,
      ALLOCATION_SIZE * 5, ALLOCATION_SIZE * 3, ALLOCATION_SIZE + 1, 0
  };
  private static final long[] TEST_SAMPLE_TIMESTAMPS = new long[] {
      0, 1000, 2000, 3000, 4000, 5000, 6000, 7000
  };
  private static final long LAST_SAMPLE_TIMESTAMP =
      TEST_SAMPLE_TIMESTAMPS[TEST_SAMPLE_TIMESTAMPS.length - 1];
  private static final int[] TEST_SAMPLE_FLAGS = new int[] {
      C.BUFFER_FLAG_KEY_FRAME, 0, 0, 0, C.BUFFER_FLAG_KEY_FRAME, 0, 0, 0
  };
  private static final Format[] TEST_SAMPLE_FORMATS = new Format[] {
      TEST_FORMAT_1, TEST_FORMAT_1, TEST_FORMAT_1, TEST_FORMAT_1, TEST_FORMAT_2, TEST_FORMAT_2,
      TEST_FORMAT_2, TEST_FORMAT_2
  };
  private static final int TEST_DATA_SECOND_KEYFRAME_INDEX = 4;

  private Allocator allocator;
  private SampleQueue sampleQueue;
  private FormatHolder formatHolder;
  private DecoderInputBuffer inputBuffer;

  @Before
  public void setUp() throws Exception {
    allocator = new DefaultAllocator(false, ALLOCATION_SIZE);
    sampleQueue = new SampleQueue(allocator);
    formatHolder = new FormatHolder();
    inputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @After
  public void tearDown() throws Exception {
    allocator = null;
    sampleQueue = null;
    formatHolder = null;
    inputBuffer = null;
  }

  @Test
  public void testResetReleasesAllocations() {
    writeTestData();
    assertAllocationCount(10);
    sampleQueue.reset();
    assertAllocationCount(0);
  }

  @Test
  public void testReadWithoutWrite() {
    assertNoSamplesToRead(null);
  }

  @Test
  public void testReadFormatDeduplicated() {
    sampleQueue.format(TEST_FORMAT_1);
    assertReadFormat(false, TEST_FORMAT_1);
    // If the same format is input then it should be de-duplicated (i.e. not output again).
    sampleQueue.format(TEST_FORMAT_1);
    assertNoSamplesToRead(TEST_FORMAT_1);
    // The same applies for a format that's equal (but a different object).
    sampleQueue.format(TEST_FORMAT_1_COPY);
    assertNoSamplesToRead(TEST_FORMAT_1);
  }

  @Test
  public void testReadSingleSamples() {
    sampleQueue.sampleData(new ParsableByteArray(TEST_DATA), ALLOCATION_SIZE);

    assertAllocationCount(1);
    // Nothing to read.
    assertNoSamplesToRead(null);

    sampleQueue.format(TEST_FORMAT_1);

    // Read the format.
    assertReadFormat(false, TEST_FORMAT_1);
    // Nothing to read.
    assertNoSamplesToRead(TEST_FORMAT_1);

    sampleQueue.sampleMetadata(1000, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, TEST_FORMAT_1);
    // Otherwise should read the sample.
    assertSampleRead(1000, true, TEST_DATA, 0, ALLOCATION_SIZE);
    // Allocation should still be held.
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // The allocation should have been released.
    assertAllocationCount(0);

    // Nothing to read.
    assertNoSamplesToRead(TEST_FORMAT_1);

    // Write a second sample followed by one byte that does not belong to it.
    sampleQueue.sampleData(new ParsableByteArray(TEST_DATA), ALLOCATION_SIZE);
    sampleQueue.sampleMetadata(2000, 0, ALLOCATION_SIZE - 1, 1, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, TEST_FORMAT_1);
    // Read the sample.
    assertSampleRead(2000, false, TEST_DATA, 0, ALLOCATION_SIZE - 1);
    // Allocation should still be held.
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // The last byte written to the sample queue may belong to a sample whose metadata has yet to be
    // written, so an allocation should still be held.
    assertAllocationCount(1);

    // Write metadata for a third sample containing the remaining byte.
    sampleQueue.sampleMetadata(3000, 0, 1, 0, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, TEST_FORMAT_1);
    // Read the sample.
    assertSampleRead(3000, false, TEST_DATA, ALLOCATION_SIZE - 1, 1);
    // Allocation should still be held.
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // The allocation should have been released.
    assertAllocationCount(0);
  }

  @Test
  public void testReadMultiSamples() {
    writeTestData();
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
    assertAllocationCount(10);
    assertReadTestData();
    assertAllocationCount(10);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
  }

  @Test
  public void testReadMultiSamplesTwice() {
    writeTestData();
    writeTestData();
    assertAllocationCount(20);
    assertReadTestData(TEST_FORMAT_2);
    assertReadTestData(TEST_FORMAT_2);
    assertAllocationCount(20);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
  }

  @Test
  public void testReadMultiWithRewind() {
    writeTestData();
    assertReadTestData();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(10);
    // Rewind.
    sampleQueue.rewind();
    assertAllocationCount(10);
    // Read again.
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertReadTestData();
  }

  @Test
  public void testRewindAfterDiscard() {
    writeTestData();
    assertReadTestData();
    sampleQueue.discardToRead();
    assertAllocationCount(0);
    // Rewind.
    sampleQueue.rewind();
    assertAllocationCount(0);
    // Can't read again.
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertReadEndOfStream(false);
  }

  @Test
  public void testAdvanceToEnd() {
    writeTestData();
    sampleQueue.advanceToEnd();
    assertAllocationCount(10);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
    // Despite skipping all samples, we should still read the last format, since this is the
    // expected format for a subsequent sample.
    assertReadFormat(false, TEST_FORMAT_2);
    // Once the format has been read, there's nothing else to read.
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testAdvanceToEndRetainsUnassignedData() {
    sampleQueue.format(TEST_FORMAT_1);
    sampleQueue.sampleData(new ParsableByteArray(TEST_DATA), ALLOCATION_SIZE);
    sampleQueue.advanceToEnd();
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    // Skipping shouldn't discard data that may belong to a sample whose metadata has yet to be
    // written.
    assertAllocationCount(1);
    // We should be able to read the format.
    assertReadFormat(false, TEST_FORMAT_1);
    // Once the format has been read, there's nothing else to read.
    assertNoSamplesToRead(TEST_FORMAT_1);

    sampleQueue.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);
    // Once the metadata has been written, check the sample can be read as expected.
    assertSampleRead(0, true, TEST_DATA, 0, ALLOCATION_SIZE);
    assertNoSamplesToRead(TEST_FORMAT_1);
    assertAllocationCount(1);
    sampleQueue.discardToRead();
    assertAllocationCount(0);
  }

  @Test
  public void testAdvanceToBeforeBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(TEST_SAMPLE_TIMESTAMPS[0] - 1, true, false);
    // Should fail and have no effect.
    assertThat(skipCount).isEqualTo(ADVANCE_FAILED);
    assertReadTestData();
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testAdvanceToStartOfBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(TEST_SAMPLE_TIMESTAMPS[0], true, false);
    // Should succeed but have no effect (we're already at the first frame).
    assertThat(skipCount).isEqualTo(0);
    assertReadTestData();
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testAdvanceToEndOfBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(LAST_SAMPLE_TIMESTAMP, true, false);
    // Should succeed and skip to 2nd keyframe (the 4th frame).
    assertThat(skipCount).isEqualTo(4);
    assertReadTestData(null, TEST_DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testAdvanceToAfterBuffer() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(LAST_SAMPLE_TIMESTAMP + 1, true, false);
    // Should fail and have no effect.
    assertThat(skipCount).isEqualTo(ADVANCE_FAILED);
    assertReadTestData();
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testAdvanceToAfterBufferAllowed() {
    writeTestData();
    int skipCount = sampleQueue.advanceTo(LAST_SAMPLE_TIMESTAMP + 1, true, true);
    // Should succeed and skip to 2nd keyframe (the 4th frame).
    assertThat(skipCount).isEqualTo(4);
    assertReadTestData(null, TEST_DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testDiscardToEnd() {
    writeTestData();
    // Should discard everything.
    sampleQueue.discardToEnd();
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    assertAllocationCount(0);
    // We should still be able to read the upstream format.
    assertReadFormat(false, TEST_FORMAT_2);
    // We should be able to write and read subsequent samples.
    writeTestData();
    assertReadTestData(TEST_FORMAT_2);
  }

  @Test
  public void testDiscardToStopAtReadPosition() {
    writeTestData();
    // Shouldn't discard anything.
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP, false, true);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertAllocationCount(10);
    // Read the first sample.
    assertReadTestData(null, 0, 1);
    // Shouldn't discard anything.
    sampleQueue.discardTo(TEST_SAMPLE_TIMESTAMPS[1] - 1, false, true);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(1);
    assertAllocationCount(10);
    // Should discard the read sample.
    sampleQueue.discardTo(TEST_SAMPLE_TIMESTAMPS[1], false, true);
    assertAllocationCount(9);
    // Shouldn't discard anything.
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP, false, true);
    assertAllocationCount(9);
    // Should be able to read the remaining samples.
    assertReadTestData(TEST_FORMAT_1, 1, 7);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(8);
    // Should discard up to the second last sample
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP - 1, false, true);
    assertAllocationCount(3);
    // Should discard up the last sample
    sampleQueue.discardTo(LAST_SAMPLE_TIMESTAMP, false, true);
    assertAllocationCount(1);
  }

  @Test
  public void testDiscardToDontStopAtReadPosition() {
    writeTestData();
    // Shouldn't discard anything.
    sampleQueue.discardTo(TEST_SAMPLE_TIMESTAMPS[1] - 1, false, false);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(0);
    assertAllocationCount(10);
    // Should discard the first sample.
    sampleQueue.discardTo(TEST_SAMPLE_TIMESTAMPS[1], false, false);
    assertThat(sampleQueue.getReadIndex()).isEqualTo(1);
    assertAllocationCount(9);
    // Should be able to read the remaining samples.
    assertReadTestData(TEST_FORMAT_1, 1, 7);
  }

  @Test
  public void testDiscardUpstream() {
    writeTestData();
    sampleQueue.discardUpstreamSamples(8);
    assertAllocationCount(10);
    sampleQueue.discardUpstreamSamples(7);
    assertAllocationCount(9);
    sampleQueue.discardUpstreamSamples(6);
    assertAllocationCount(7);
    sampleQueue.discardUpstreamSamples(5);
    assertAllocationCount(5);
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(4);
    sampleQueue.discardUpstreamSamples(3);
    assertAllocationCount(3);
    sampleQueue.discardUpstreamSamples(2);
    assertAllocationCount(2);
    sampleQueue.discardUpstreamSamples(1);
    assertAllocationCount(1);
    sampleQueue.discardUpstreamSamples(0);
    assertAllocationCount(0);
    assertReadFormat(false, TEST_FORMAT_2);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testDiscardUpstreamMulti() {
    writeTestData();
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(4);
    sampleQueue.discardUpstreamSamples(0);
    assertAllocationCount(0);
    assertReadFormat(false, TEST_FORMAT_2);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testDiscardUpstreamBeforeRead() {
    writeTestData();
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(4);
    assertReadTestData(null, 0, 4);
    assertReadFormat(false, TEST_FORMAT_2);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testDiscardUpstreamAfterRead() {
    writeTestData();
    assertReadTestData(null, 0, 3);
    sampleQueue.discardUpstreamSamples(8);
    assertAllocationCount(10);
    sampleQueue.discardToRead();
    assertAllocationCount(7);
    sampleQueue.discardUpstreamSamples(7);
    assertAllocationCount(6);
    sampleQueue.discardUpstreamSamples(6);
    assertAllocationCount(4);
    sampleQueue.discardUpstreamSamples(5);
    assertAllocationCount(2);
    sampleQueue.discardUpstreamSamples(4);
    assertAllocationCount(1);
    sampleQueue.discardUpstreamSamples(3);
    assertAllocationCount(0);
    assertReadFormat(false, TEST_FORMAT_2);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  @Test
  public void testLargestQueuedTimestampWithDiscardUpstream() {
    writeTestData();
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
    sampleQueue.discardUpstreamSamples(TEST_SAMPLE_TIMESTAMPS.length - 1);
    // Discarding from upstream should reduce the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs())
        .isEqualTo(TEST_SAMPLE_TIMESTAMPS[TEST_SAMPLE_TIMESTAMPS.length - 2]);
    sampleQueue.discardUpstreamSamples(0);
    // Discarding everything from upstream without reading should unset the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(MIN_VALUE);
  }

  @Test
  public void testLargestQueuedTimestampWithDiscardUpstreamDecodeOrder() {
    long[] decodeOrderTimestamps = new long[] {0, 3000, 2000, 1000, 4000, 7000, 6000, 5000};
    writeTestData(TEST_DATA, TEST_SAMPLE_SIZES, TEST_SAMPLE_OFFSETS, decodeOrderTimestamps,
        TEST_SAMPLE_FORMATS, TEST_SAMPLE_FLAGS);
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(7000);
    sampleQueue.discardUpstreamSamples(TEST_SAMPLE_TIMESTAMPS.length - 2);
    // Discarding the last two samples should not change the largest timestamp, due to the decode
    // ordering of the timestamps.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(7000);
    sampleQueue.discardUpstreamSamples(TEST_SAMPLE_TIMESTAMPS.length - 3);
    // Once a third sample is discarded, the largest timestamp should have changed.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(4000);
    sampleQueue.discardUpstreamSamples(0);
    // Discarding everything from upstream without reading should unset the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(MIN_VALUE);
  }

  @Test
  public void testLargestQueuedTimestampWithRead() {
    writeTestData();
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
    assertReadTestData();
    // Reading everything should not reduce the largest timestamp.
    assertThat(sampleQueue.getLargestQueuedTimestampUs()).isEqualTo(LAST_SAMPLE_TIMESTAMP);
  }

  // Internal methods.

  /**
   * Writes standard test data to {@code sampleQueue}.
   */
  @SuppressWarnings("ReferenceEquality")
  private void writeTestData() {
    writeTestData(TEST_DATA, TEST_SAMPLE_SIZES, TEST_SAMPLE_OFFSETS, TEST_SAMPLE_TIMESTAMPS,
        TEST_SAMPLE_FORMATS, TEST_SAMPLE_FLAGS);
  }

  /**
   * Writes the specified test data to {@code sampleQueue}.
   *
   *
   */
  @SuppressWarnings("ReferenceEquality")
  private void writeTestData(byte[] data, int[] sampleSizes, int[] sampleOffsets,
      long[] sampleTimestamps, Format[] sampleFormats, int[] sampleFlags) {
    sampleQueue.sampleData(new ParsableByteArray(data), data.length);
    Format format = null;
    for (int i = 0; i < sampleTimestamps.length; i++) {
      if (sampleFormats[i] != format) {
        sampleQueue.format(sampleFormats[i]);
        format = sampleFormats[i];
      }
      sampleQueue.sampleMetadata(sampleTimestamps[i], sampleFlags[i], sampleSizes[i],
          sampleOffsets[i], null);
    }
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   */
  private void assertReadTestData() {
    assertReadTestData(null, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   */
  private void assertReadTestData(Format startFormat) {
    assertReadTestData(startFormat, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   * @param firstSampleIndex The index of the first sample that's expected to be read.
   */
  private void assertReadTestData(Format startFormat, int firstSampleIndex) {
    assertReadTestData(startFormat, firstSampleIndex,
        TEST_SAMPLE_TIMESTAMPS.length - firstSampleIndex);
  }

  /**
   * Asserts correct reading of standard test data from {@code sampleQueue}.
   *
   * @param startFormat The format of the last sample previously read from {@code sampleQueue}.
   * @param firstSampleIndex The index of the first sample that's expected to be read.
   * @param sampleCount The number of samples to read.
   */
  private void assertReadTestData(Format startFormat, int firstSampleIndex, int sampleCount) {
    Format format = startFormat;
    for (int i = firstSampleIndex; i < firstSampleIndex + sampleCount; i++) {
      // Use equals() on the read side despite using referential equality on the write side, since
      // sampleQueue de-duplicates written formats using equals().
      if (!TEST_SAMPLE_FORMATS[i].equals(format)) {
        // If the format has changed, we should read it.
        assertReadFormat(false, TEST_SAMPLE_FORMATS[i]);
        format = TEST_SAMPLE_FORMATS[i];
      }
      // If we require the format, we should always read it.
      assertReadFormat(true, TEST_SAMPLE_FORMATS[i]);
      // Assert the sample is as expected.
      assertSampleRead(TEST_SAMPLE_TIMESTAMPS[i],
          (TEST_SAMPLE_FLAGS[i] & C.BUFFER_FLAG_KEY_FRAME) != 0,
          TEST_DATA,
          TEST_DATA.length - TEST_SAMPLE_OFFSETS[i] - TEST_SAMPLE_SIZES[i],
          TEST_SAMPLE_SIZES[i]);
    }
  }

  /**
   * Asserts {@link SampleQueue#read} is behaving correctly, given there are no samples to read and
   * the last format to be written to the sample queue is {@code endFormat}.
   *
   * @param endFormat The last format to be written to the sample queue, or null of no format has
   *     been written.
   */
  private void assertNoSamplesToRead(Format endFormat) {
    // If not formatRequired or loadingFinished, should read nothing.
    assertReadNothing(false);
    // If formatRequired, should read the end format if set, else read nothing.
    if (endFormat == null) {
      assertReadNothing(true);
    } else {
      assertReadFormat(true, endFormat);
    }
    // If loadingFinished, should read end of stream.
    assertReadEndOfStream(false);
    assertReadEndOfStream(true);
    // Having read end of stream should not affect other cases.
    assertReadNothing(false);
    if (endFormat == null) {
      assertReadNothing(true);
    } else {
      assertReadFormat(true, endFormat);
    }
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_NOTHING_READ}.
   *
   * @param formatRequired The value of {@code formatRequired} passed to readData.
   */
  private void assertReadNothing(boolean formatRequired) {
    clearFormatHolderAndInputBuffer();
    int result = sampleQueue.read(formatHolder, inputBuffer, formatRequired, false, 0);
    assertThat(result).isEqualTo(RESULT_NOTHING_READ);
    // formatHolder should not be populated.
    assertThat(formatHolder.format).isNull();
    // inputBuffer should not be populated.
    assertInputBufferContainsNoSampleData();
    assertInputBufferHasNoDefaultFlagsSet();
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_BUFFER_READ} and that the
   * {@link DecoderInputBuffer#isEndOfStream()} is set.
   *
   * @param formatRequired The value of {@code formatRequired} passed to readData.
   */
  private void assertReadEndOfStream(boolean formatRequired) {
    clearFormatHolderAndInputBuffer();
    int result = sampleQueue.read(formatHolder, inputBuffer, formatRequired, true, 0);
    assertThat(result).isEqualTo(RESULT_BUFFER_READ);
    // formatHolder should not be populated.
    assertThat(formatHolder.format).isNull();
    // inputBuffer should not contain sample data, but end of stream flag should be set.
    assertInputBufferContainsNoSampleData();
    assertThat(inputBuffer.isEndOfStream()).isTrue();
    assertThat(inputBuffer.isDecodeOnly()).isFalse();
    assertThat(inputBuffer.isEncrypted()).isFalse();
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_FORMAT_READ} and that the format
   * holder is filled with a {@link Format} that equals {@code format}.
   *
   * @param formatRequired The value of {@code formatRequired} passed to readData.
   * @param format The expected format.
   */
  private void assertReadFormat(boolean formatRequired, Format format) {
    clearFormatHolderAndInputBuffer();
    int result = sampleQueue.read(formatHolder, inputBuffer, formatRequired, false, 0);
    assertThat(result).isEqualTo(RESULT_FORMAT_READ);
    // formatHolder should be populated.
    assertThat(formatHolder.format).isEqualTo(format);
    // inputBuffer should not be populated.
    assertInputBufferContainsNoSampleData();
    assertInputBufferHasNoDefaultFlagsSet();
  }

  /**
   * Asserts {@link SampleQueue#read} returns {@link C#RESULT_BUFFER_READ} and that the buffer is
   * filled with the specified sample data.
   *
   * @param timeUs The expected buffer timestamp.
   * @param isKeyframe The expected keyframe flag.
   * @param sampleData An array containing the expected sample data.
   * @param offset The offset in {@code sampleData} of the expected sample data.
   * @param length The length of the expected sample data.
   */
  private void assertSampleRead(long timeUs, boolean isKeyframe, byte[] sampleData, int offset,
      int length) {
    clearFormatHolderAndInputBuffer();
    int result = sampleQueue.read(formatHolder, inputBuffer, false, false, 0);
    assertThat(result).isEqualTo(RESULT_BUFFER_READ);
    // formatHolder should not be populated.
    assertThat(formatHolder.format).isNull();
    // inputBuffer should be populated.
    assertThat(inputBuffer.timeUs).isEqualTo(timeUs);
    assertThat(inputBuffer.isKeyFrame()).isEqualTo(isKeyframe);
    assertThat(inputBuffer.isDecodeOnly()).isFalse();
    assertThat(inputBuffer.isEncrypted()).isFalse();
    inputBuffer.flip();
    assertThat(inputBuffer.data.limit()).isEqualTo(length);
    byte[] readData = new byte[length];
    inputBuffer.data.get(readData);
    assertThat(readData).isEqualTo(copyOfRange(sampleData, offset, offset + length));
  }

  /**
   * Asserts the number of allocations currently in use by {@code sampleQueue}.
   *
   * @param count The expected number of allocations.
   */
  private void assertAllocationCount(int count) {
    assertThat(allocator.getTotalBytesAllocated()).isEqualTo(ALLOCATION_SIZE * count);
  }

  /**
   * Asserts {@code inputBuffer} does not contain any sample data.
   */
  private void assertInputBufferContainsNoSampleData() {
    if (inputBuffer.data == null) {
      return;
    }
    inputBuffer.flip();
    assertThat(inputBuffer.data.limit()).isEqualTo(0);
  }

  private void assertInputBufferHasNoDefaultFlagsSet() {
    assertThat(inputBuffer.isEndOfStream()).isFalse();
    assertThat(inputBuffer.isDecodeOnly()).isFalse();
    assertThat(inputBuffer.isEncrypted()).isFalse();
  }

  private void clearFormatHolderAndInputBuffer() {
    formatHolder.format = null;
    inputBuffer.clear();
  }

}
