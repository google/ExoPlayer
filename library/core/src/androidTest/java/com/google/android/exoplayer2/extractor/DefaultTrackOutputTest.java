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
package com.google.android.exoplayer2.extractor;

import android.test.MoreAsserts;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * Test for {@link DefaultTrackOutput}.
 */
public class DefaultTrackOutputTest extends TestCase {

  private static final int ALLOCATION_SIZE = 16;

  private static final Format TEST_FORMAT_1 = Format.createSampleFormat("1", "mimeType", 0);
  private static final Format TEST_FORMAT_2 = Format.createSampleFormat("2", "mimeType", 0);
  private static final Format TEST_FORMAT_1_COPY = Format.createSampleFormat("1", "mimeType", 0);
  private static final byte[] TEST_DATA = TestUtil.buildTestData(ALLOCATION_SIZE * 10);

  /*
   * TEST_SAMPLE_SIZES and TEST_SAMPLE_OFFSETS are intended to test various boundary cases (with
   * respect to the allocation size). TEST_SAMPLE_OFFSETS values are defined as the backward offsets
   * (as expected by DefaultTrackOutput.sampleMetadata) assuming that TEST_DATA has been written to
   * the trackOutput in full. The allocations are filled as follows, where | indicates a boundary
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
  private static final int[] TEST_SAMPLE_TIMESTAMPS = new int[] {
      0, 1000, 2000, 3000, 4000, 5000, 6000, 7000
  };
  private static final int[] TEST_SAMPLE_FLAGS = new int[] {
      C.BUFFER_FLAG_KEY_FRAME, 0, 0, 0, C.BUFFER_FLAG_KEY_FRAME, 0, 0, 0
  };
  private static final Format[] TEST_SAMPLE_FORMATS = new Format[] {
      TEST_FORMAT_1, TEST_FORMAT_1, TEST_FORMAT_1, TEST_FORMAT_1, TEST_FORMAT_2, TEST_FORMAT_2,
      TEST_FORMAT_2, TEST_FORMAT_2
  };
  private static final int TEST_DATA_SECOND_KEYFRAME_INDEX = 4;

  private Allocator allocator;
  private DefaultTrackOutput trackOutput;
  private FormatHolder formatHolder;
  private DecoderInputBuffer inputBuffer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    allocator = new DefaultAllocator(false, ALLOCATION_SIZE);
    trackOutput = new DefaultTrackOutput(allocator);
    formatHolder = new FormatHolder();
    inputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    allocator = null;
    trackOutput = null;
    formatHolder = null;
    inputBuffer = null;
  }

  public void testReadWithoutWrite() {
    assertNoSamplesToRead(null);
  }

  public void testReadFormatDeduplicated() {
    trackOutput.format(TEST_FORMAT_1);
    assertReadFormat(false, TEST_FORMAT_1);
    // If the same format is input then it should be de-duplicated (i.e. not output again).
    trackOutput.format(TEST_FORMAT_1);
    assertNoSamplesToRead(TEST_FORMAT_1);
    // The same applies for a format that's equal (but a different object).
    trackOutput.format(TEST_FORMAT_1_COPY);
    assertNoSamplesToRead(TEST_FORMAT_1);
  }

  public void testReadSingleSamples() {
    trackOutput.sampleData(new ParsableByteArray(TEST_DATA), ALLOCATION_SIZE);

    assertAllocationCount(1);
    // Nothing to read.
    assertNoSamplesToRead(null);

    trackOutput.format(TEST_FORMAT_1);

    // Read the format.
    assertReadFormat(false, TEST_FORMAT_1);
    // Nothing to read.
    assertNoSamplesToRead(TEST_FORMAT_1);

    trackOutput.sampleMetadata(1000, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, TEST_FORMAT_1);
    // Otherwise should read the sample.
    assertSampleRead(1000, true, TEST_DATA, 0, ALLOCATION_SIZE);
    // The allocation should have been released.
    assertAllocationCount(0);

    // Nothing to read.
    assertNoSamplesToRead(TEST_FORMAT_1);

    // Write a second sample followed by one byte that does not belong to it.
    trackOutput.sampleData(new ParsableByteArray(TEST_DATA), ALLOCATION_SIZE);
    trackOutput.sampleMetadata(2000, 0, ALLOCATION_SIZE - 1, 1, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, TEST_FORMAT_1);
    // Read the sample.
    assertSampleRead(2000, false, TEST_DATA, 0, ALLOCATION_SIZE - 1);
    // The last byte written to the output may belong to a sample whose metadata has yet to be
    // written, so an allocation should still be held.
    assertAllocationCount(1);

    // Write metadata for a third sample containing the remaining byte.
    trackOutput.sampleMetadata(3000, 0, 1, 0, null);

    // If formatRequired, should read the format rather than the sample.
    assertReadFormat(true, TEST_FORMAT_1);
    // Read the sample.
    assertSampleRead(3000, false, TEST_DATA, ALLOCATION_SIZE - 1, 1);
    // The allocation should have been released.
    assertAllocationCount(0);
  }

  public void testReadMultiSamples() {
    writeTestData();
    assertEquals(TEST_SAMPLE_TIMESTAMPS[TEST_SAMPLE_TIMESTAMPS.length - 1],
        trackOutput.getLargestQueuedTimestampUs());
    assertAllocationCount(10);
    assertReadTestData();
    assertAllocationCount(0);
  }

  public void testReadMultiSamplesTwice() {
    writeTestData();
    writeTestData();
    assertAllocationCount(20);
    assertReadTestData(TEST_FORMAT_2);
    assertReadTestData(TEST_FORMAT_2);
    assertAllocationCount(0);
  }

  public void testSkipAll() {
    writeTestData();
    trackOutput.skipAll();
    assertAllocationCount(0);
    // Despite skipping all samples, we should still read the last format, since this is the
    // expected format for a subsequent sample.
    assertReadFormat(false, TEST_FORMAT_2);
    // Once the format has been read, there's nothing else to read.
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  public void testSkipAllRetainsUnassignedData() {
    trackOutput.format(TEST_FORMAT_1);
    trackOutput.sampleData(new ParsableByteArray(TEST_DATA), ALLOCATION_SIZE);
    trackOutput.skipAll();
    // Skipping shouldn't discard data that may belong to a sample whose metadata has yet to be
    // written.
    assertAllocationCount(1);
    // We should be able to read the format.
    assertReadFormat(false, TEST_FORMAT_1);
    // Once the format has been read, there's nothing else to read.
    assertNoSamplesToRead(TEST_FORMAT_1);

    trackOutput.sampleMetadata(0, C.BUFFER_FLAG_KEY_FRAME, ALLOCATION_SIZE, 0, null);
    // Once the metadata has been written, check the sample can be read as expected.
    assertSampleRead(0, true, TEST_DATA, 0, ALLOCATION_SIZE);
    assertNoSamplesToRead(TEST_FORMAT_1);
    assertAllocationCount(0);
  }

  public void testSkipToKeyframeBeforeBuffer() {
    writeTestData();
    boolean result = trackOutput.skipToKeyframeBefore(TEST_SAMPLE_TIMESTAMPS[0] - 1, false);
    // Should fail and have no effect.
    assertFalse(result);
    assertReadTestData();
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  public void testSkipToKeyframeStartOfBuffer() {
    writeTestData();
    boolean result = trackOutput.skipToKeyframeBefore(TEST_SAMPLE_TIMESTAMPS[0], false);
    // Should succeed but have no effect (we're already at the first frame).
    assertTrue(result);
    assertReadTestData();
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  public void testSkipToKeyframeEndOfBuffer() {
    writeTestData();
    boolean result = trackOutput.skipToKeyframeBefore(
        TEST_SAMPLE_TIMESTAMPS[TEST_SAMPLE_TIMESTAMPS.length - 1], false);
    // Should succeed and skip to 2nd keyframe.
    assertTrue(result);
    assertReadTestData(null, TEST_DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  public void testSkipToKeyframeAfterBuffer() {
    writeTestData();
    boolean result = trackOutput.skipToKeyframeBefore(
        TEST_SAMPLE_TIMESTAMPS[TEST_SAMPLE_TIMESTAMPS.length - 1] + 1, false);
    // Should fail and have no effect.
    assertFalse(result);
    assertReadTestData();
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  public void testSkipToKeyframeAfterBufferAllowed() {
    writeTestData();
    boolean result = trackOutput.skipToKeyframeBefore(
        TEST_SAMPLE_TIMESTAMPS[TEST_SAMPLE_TIMESTAMPS.length - 1] + 1, true);
    // Should succeed and skip to 2nd keyframe.
    assertTrue(result);
    assertReadTestData(null, TEST_DATA_SECOND_KEYFRAME_INDEX);
    assertNoSamplesToRead(TEST_FORMAT_2);
  }

  // Internal methods.

  /**
   * Writes standard test data to {@code trackOutput}.
   */
  @SuppressWarnings("ReferenceEquality")
  private void writeTestData() {
    trackOutput.sampleData(new ParsableByteArray(TEST_DATA), TEST_DATA.length);
    Format format = null;
    for (int i = 0; i < TEST_SAMPLE_TIMESTAMPS.length; i++) {
      if (TEST_SAMPLE_FORMATS[i] != format) {
        trackOutput.format(TEST_SAMPLE_FORMATS[i]);
        format = TEST_SAMPLE_FORMATS[i];
      }
      trackOutput.sampleMetadata(TEST_SAMPLE_TIMESTAMPS[i], TEST_SAMPLE_FLAGS[i],
          TEST_SAMPLE_SIZES[i], TEST_SAMPLE_OFFSETS[i], null);
    }
  }

  /**
   * Asserts correct reading of standard test data from {@code trackOutput}.
   */
  private void assertReadTestData() {
    assertReadTestData(null, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code trackOutput}.
   *
   * @param startFormat The format of the last sample previously read from {@code trackOutput}.
   */
  private void assertReadTestData(Format startFormat) {
    assertReadTestData(startFormat, 0);
  }

  /**
   * Asserts correct reading of standard test data from {@code trackOutput}.
   *
   * @param startFormat The format of the last sample previously read from {@code trackOutput}.
   * @param firstSampleIndex The index of the first sample that's expected to be read.
   */
  private void assertReadTestData(Format startFormat, int firstSampleIndex) {
    Format format = startFormat;
    for (int i = firstSampleIndex; i < TEST_SAMPLE_TIMESTAMPS.length; i++) {
      // Use equals() on the read side despite using referential equality on the write side, since
      // trackOutput de-duplicates written formats using equals().
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
   * Asserts {@link DefaultTrackOutput#readData} is behaving correctly, given there are no samples
   * to read and the last format to be written to the output is {@code endFormat}.
   *
   * @param endFormat The last format to be written to the output, or null of no format has been
   *     written.
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
   * Asserts {@link DefaultTrackOutput#readData} returns {@link C#RESULT_NOTHING_READ}.
   *
   * @param formatRequired The value of {@code formatRequired} passed to readData.
   */
  private void assertReadNothing(boolean formatRequired) {
    clearFormatHolderAndInputBuffer();
    int result = trackOutput.readData(formatHolder, inputBuffer, formatRequired, false, 0);
    assertEquals(C.RESULT_NOTHING_READ, result);
    // formatHolder should not be populated.
    assertNull(formatHolder.format);
    // inputBuffer should not be populated.
    assertInputBufferContainsNoSampleData();
    assertInputBufferHasNoDefaultFlagsSet();
  }

  /**
   * Asserts {@link DefaultTrackOutput#readData} returns {@link C#RESULT_BUFFER_READ} and that the
   * {@link DecoderInputBuffer#isEndOfStream()} is set.
   *
   * @param formatRequired The value of {@code formatRequired} passed to readData.
   */
  private void assertReadEndOfStream(boolean formatRequired) {
    clearFormatHolderAndInputBuffer();
    int result = trackOutput.readData(formatHolder, inputBuffer, formatRequired, true, 0);
    assertEquals(C.RESULT_BUFFER_READ, result);
    // formatHolder should not be populated.
    assertNull(formatHolder.format);
    // inputBuffer should not contain sample data, but end of stream flag should be set.
    assertInputBufferContainsNoSampleData();
    assertTrue(inputBuffer.isEndOfStream());
    assertFalse(inputBuffer.isDecodeOnly());
    assertFalse(inputBuffer.isEncrypted());
  }

  /**
   * Asserts {@link DefaultTrackOutput#readData} returns {@link C#RESULT_FORMAT_READ} and that the
   * format holder is filled with a {@link Format} that equals {@code format}.
   *
   * @param formatRequired The value of {@code formatRequired} passed to readData.
   * @param format The expected format.
   */
  private void assertReadFormat(boolean formatRequired, Format format) {
    clearFormatHolderAndInputBuffer();
    int result = trackOutput.readData(formatHolder, inputBuffer, formatRequired, false, 0);
    assertEquals(C.RESULT_FORMAT_READ, result);
    // formatHolder should be populated.
    assertEquals(format, formatHolder.format);
    // inputBuffer should not be populated.
    assertInputBufferContainsNoSampleData();
    assertInputBufferHasNoDefaultFlagsSet();
  }

  /**
   * Asserts {@link DefaultTrackOutput#readData} returns {@link C#RESULT_BUFFER_READ} and that the
   * buffer is filled with the specified sample data.
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
    int result = trackOutput.readData(formatHolder, inputBuffer, false, false, 0);
    assertEquals(C.RESULT_BUFFER_READ, result);
    // formatHolder should not be populated.
    assertNull(formatHolder.format);
    // inputBuffer should be populated.
    assertEquals(timeUs, inputBuffer.timeUs);
    assertEquals(isKeyframe, inputBuffer.isKeyFrame());
    assertFalse(inputBuffer.isDecodeOnly());
    assertFalse(inputBuffer.isEncrypted());
    inputBuffer.flip();
    assertEquals(length, inputBuffer.data.limit());
    byte[] readData = new byte[length];
    inputBuffer.data.get(readData);
    MoreAsserts.assertEquals(Arrays.copyOfRange(sampleData, offset, offset + length), readData);
  }

  /**
   * Asserts the number of allocations currently in use by {@code trackOutput}.
   *
   * @param count The expected number of allocations.
   */
  private void assertAllocationCount(int count) {
    assertEquals(ALLOCATION_SIZE * count, allocator.getTotalBytesAllocated());
  }

  /**
   * Asserts {@code inputBuffer} does not contain any sample data.
   */
  private void assertInputBufferContainsNoSampleData() {
    if (inputBuffer.data == null) {
      return;
    }
    inputBuffer.flip();
    assertEquals(0, inputBuffer.data.limit());
  }

  private void assertInputBufferHasNoDefaultFlagsSet() {
    assertFalse(inputBuffer.isEndOfStream());
    assertFalse(inputBuffer.isDecodeOnly());
    assertFalse(inputBuffer.isEncrypted());
  }

  private void clearFormatHolderAndInputBuffer() {
    formatHolder.format = null;
    inputBuffer.clear();
  }

}
