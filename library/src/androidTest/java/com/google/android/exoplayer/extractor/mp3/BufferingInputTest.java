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
package com.google.android.exoplayer.extractor.mp3;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.android.exoplayer.extractor.DefaultExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.testutil.FakeDataSource;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.net.Uri;
import android.test.InstrumentationTestCase;

import org.mockito.Mock;

import java.nio.BufferOverflowException;
import java.util.Arrays;

/**
 * Tests for {@link BufferingInput}.
 */
public class BufferingInputTest extends InstrumentationTestCase {

  private static final String TEST_URI = "http://www.google.com";
  private static final byte[] STREAM_DATA = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

  private ExtractorInput fakeExtractorInput;

  /** Used for verifying interactions. */
  @Mock private ExtractorInput mockExtractorInput;
  @Mock private TrackOutput mockTrackOutput;

  @Override
  public void setUp() throws Exception {
    TestUtil.setUpMockito(this);

    FakeDataSource.Builder builder = new FakeDataSource.Builder();
    builder.appendReadData(STREAM_DATA);
    FakeDataSource fakeDataSource = builder.build();
    fakeDataSource.open(new DataSpec(Uri.parse(TEST_URI)));
    fakeExtractorInput = new DefaultExtractorInput(fakeDataSource, 0, STREAM_DATA.length);
  }

  public void testReadFromExtractor() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[4];
    input.read(fakeExtractorInput, target, 0, 4);
    assertMatchesStreamData(target, 0, 4);
  }

  public void testReadCapacityFromExtractor() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[5];
    input.read(fakeExtractorInput, target, 0, 5);
    assertMatchesStreamData(target, 0, 5);
  }

  public void testReadOverCapacityFromExtractorFails() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[6];
    try {
      input.read(fakeExtractorInput, target, 0, 6);
      fail();
    } catch (BufferOverflowException e) {
      // Expected.
    }
  }

  public void testReadFromBuffer() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[5];
    input.read(fakeExtractorInput, target, 0, 5);

    // When reading already-buffered data
    input.returnToMark();
    input.read(mockExtractorInput, target, 0, 5);
    assertMatchesStreamData(target, 0, 5);

    // There is no interaction with the extractor input.
    verifyZeroInteractions(mockExtractorInput);
  }

  public void testReadFromBufferPartially() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[5];
    input.read(fakeExtractorInput, target, 0, 5);

    // When reading already-buffered data
    input.returnToMark();
    input.read(mockExtractorInput, target, 0, 4);
    assertMatchesStreamData(target, 0, 4);

    // There is no interaction with the extractor input.
    verifyZeroInteractions(mockExtractorInput);
  }

  public void testResetDiscardsData() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[5];
    input.read(fakeExtractorInput, target, 0, 5);

    // When the buffer is reset
    input.reset();

    // Then it is possible to read up to the capacity again.
    input.read(fakeExtractorInput, target, 0, 5);
    assertMatchesStreamData(target, 5, 5);
  }

  public void testGetAvailableByteCountAtWritePosition() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[5];
    input.read(fakeExtractorInput, target, 0, 5);
    assertEquals(0, input.getAvailableByteCount());
  }

  public void testGetAvailableByteCountBeforeWritePosition() throws Exception {
    BufferingInput input = new BufferingInput(5);
    byte[] target = new byte[5];
    input.read(fakeExtractorInput, target, 0, 3);
    input.mark();
    input.read(fakeExtractorInput, target, 0, 3);
    input.mark();
    input.read(fakeExtractorInput, target, 0, 2);
    input.returnToMark();

    // The reading position is calculated correctly.
    assertEquals(2, input.getAvailableByteCount());
    assertEquals(8, fakeExtractorInput.getPosition());
  }

  public void testGetParsableByteArray() throws Exception {
    BufferingInput input = new BufferingInput(5);
    input.skip(fakeExtractorInput, 4);
    input.mark();
    input.skip(fakeExtractorInput, 3);
    input.returnToMark();
    ParsableByteArray parsableByteArray = input.getParsableByteArray(fakeExtractorInput, 4);

    // The returned array matches the input's internal buffer.
    assertMatchesStreamData(parsableByteArray.data, 0, 7);
  }

  public void testGetParsableByteArrayPastCapacity() throws Exception {
    BufferingInput input = new BufferingInput(5);
    input.skip(fakeExtractorInput, 4);
    input.mark();
    input.skip(fakeExtractorInput, 3);
    input.mark();
    input.skip(fakeExtractorInput, 1);
    input.returnToMark();
    ParsableByteArray parsableByteArray = input.getParsableByteArray(fakeExtractorInput, 2);

    // The second call to mark() copied the buffer data to the start.
    assertMatchesStreamData(parsableByteArray.data, 7, 2);
  }

  public void testDrainEntireBuffer() throws Exception {
    BufferingInput input = new BufferingInput(5);
    input.skip(fakeExtractorInput, 3);
    input.returnToMark();

    // When draining the first three bytes
    input.drainToOutput(mockTrackOutput, 3);

    // They are appended as sample data.
    verify(mockTrackOutput).sampleData(any(ParsableByteArray.class), eq(3));
  }

  public void testDrainTwice() throws Exception {
    BufferingInput input = new BufferingInput(5);
    input.skip(fakeExtractorInput, 3);
    input.returnToMark();

    // When draining one then two bytes
    input.drainToOutput(mockTrackOutput, 1);
    assertEquals(2, input.drainToOutput(mockTrackOutput, 3));

    // They are appended as sample data.
    verify(mockTrackOutput).sampleData(any(ParsableByteArray.class), eq(1));
    verify(mockTrackOutput).sampleData(any(ParsableByteArray.class), eq(2));
  }

  public void testDrainPastCapacity() throws Exception {
    BufferingInput input = new BufferingInput(5);
    input.skip(fakeExtractorInput, 4);
    input.mark();
    input.skip(fakeExtractorInput, 5);
    input.returnToMark();

    // When draining the entire buffer
    input.drainToOutput(mockTrackOutput, 5);

    // The sample data is appended as one whole buffer.
    verify(mockTrackOutput).sampleData(any(ParsableByteArray.class), eq(5));
  }

  private static void assertMatchesStreamData(byte[] read, int offset, int length) {
    assertTrue(Arrays.equals(Arrays.copyOfRange(STREAM_DATA, offset, offset + length),
        Arrays.copyOfRange(read, 0, length)));
  }

}
