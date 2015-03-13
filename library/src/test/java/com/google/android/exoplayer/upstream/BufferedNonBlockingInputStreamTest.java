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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer.SampleSource;

import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;

/**
 * Tests for {@link BufferedNonBlockingInputStream}.
 */
public class BufferedNonBlockingInputStreamTest extends TestCase {

  private static final int BUFFER_SIZE_BYTES = 16;

  @Mock private NonBlockingInputStream mockInputStream;
  private BufferedNonBlockingInputStream bufferedInputStream;

  @Override
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    bufferedInputStream = new BufferedNonBlockingInputStream(mockInputStream, BUFFER_SIZE_BYTES);
  }

  public void testSkipClipsCountToBufferSizeWhenMarkSet() {
    // When marking and skipping more than the buffer size
    bufferedInputStream.mark();
    bufferedInputStream.skip(BUFFER_SIZE_BYTES + 1);

    // Then BUFFER_SIZE_BYTES are read.
    verify(mockInputStream).read((byte[]) any(), eq(0), eq(BUFFER_SIZE_BYTES));
  }

  public void testSkipResetSkipUsesBufferedData() {
    // Given a buffered input stream that has already read BUFFER_SIZE_BYTES
    stubInputStreamForReadingBytes();
    bufferedInputStream.mark();
    bufferedInputStream.skip(BUFFER_SIZE_BYTES);
    verify(mockInputStream).read((byte[]) any(), eq(0), eq(BUFFER_SIZE_BYTES));

    // When resetting and reading the same amount, no extra data are read.
    bufferedInputStream.returnToMark();
    bufferedInputStream.skip(BUFFER_SIZE_BYTES);
    verify(mockInputStream).read((byte[]) any(), eq(0), eq(BUFFER_SIZE_BYTES));
  }

  public void testReturnsEndOfStreamAfterBufferedData() {
    // Given a buffered input stream that has read 1 byte (to end-of-stream) and has been reset
    stubInputStreamForReadingBytes();
    bufferedInputStream.mark();
    bufferedInputStream.skip(1);
    stubInputStreamForReadingEndOfStream();
    bufferedInputStream.returnToMark();

    // When skipping, first 1 byte is returned, then end-of-stream.
    assertEquals(1, bufferedInputStream.skip(1));
    assertEquals(SampleSource.END_OF_STREAM, bufferedInputStream.skip(1));
  }

  public void testReadAtOffset() {
    // Given a mock input stream that provide non-zero data
    stubInputStreamForReadingBytes();

    // When reading a byte at offset 1
    byte[] bytes = new byte[2];
    bufferedInputStream.mark();
    bufferedInputStream.read(bytes, 1, 1);

    // Then only the second byte is set.
    assertTrue(Arrays.equals(new byte[] {(byte) 0, (byte) 0xFF}, bytes));
  }

  public void testSkipAfterMark() {
    // Given a mock input stream that provides non-zero data, with three bytes read
    stubInputStreamForReadingBytes();
    bufferedInputStream.skip(1);
    bufferedInputStream.mark();
    bufferedInputStream.skip(2);
    bufferedInputStream.returnToMark();

    // Then it is possible to skip one byte after the mark and read two bytes.
    assertEquals(1, bufferedInputStream.skip(1));
    assertEquals(2, bufferedInputStream.read(new byte[2], 0, 2));
    verify(mockInputStream).read((byte[]) any(), eq(0), eq(1));
    verify(mockInputStream).read((byte[]) any(), eq(0), eq(2));
    verify(mockInputStream).read((byte[]) any(), eq(2), eq(1));
  }

  /** Stubs the input stream to read 0xFF for all requests. */
  private void stubInputStreamForReadingBytes() {
    when(mockInputStream.read((byte[]) any(), anyInt(), anyInt())).thenAnswer(
        new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        byte[] bytes = (byte[]) invocation.getArguments()[0];
        int offset = (int) invocation.getArguments()[1];
        int length = (int) invocation.getArguments()[2];
        for (int i = 0; i < length; i++) {
          bytes[i + offset] = (byte) 0xFF;
        }
        return length;
      }

    });
    when(mockInputStream.skip(anyInt())).thenAnswer(new Answer<Integer>() {

      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        return (int) invocation.getArguments()[0];
      }

    });
  }

  /** Stubs the input stream to read end-of-stream for all requests. */
  private void stubInputStreamForReadingEndOfStream() {
    when(mockInputStream.read((byte[]) any(), anyInt(), anyInt()))
        .thenReturn(SampleSource.END_OF_STREAM);
  }

}
