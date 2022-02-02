/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.Test;

public class NalChunkPeekerTest {
  @Test
  public void construct_givenTooSmallPeekSize() {
    try {
      new MockNalChunkPeeker(4, false);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      //Intentionally blank
    }
  }

  @Test
  public void peek_givenNoData() {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().build();
    final MockNalChunkPeeker peeker = new MockNalChunkPeeker(5, false);
    try {
      peeker.peek(input, 10);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }
  @Test
  public void peek_givenNoNal() {
    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[10]).build();
    final MockNalChunkPeeker peeker = new MockNalChunkPeeker(5, false);
    try {
      peeker.peek(input, 10);
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }
  @Test
  public void peek_givenAlwaysSkip() {
    final ByteBuffer byteBuffer = AviExtractor.allocate(10);
    DataHelper.appendNal(byteBuffer, (byte)32);

    final FakeExtractorInput input = new FakeExtractorInput.Builder().setData(byteBuffer.array()).build();
    final MockNalChunkPeeker peeker = new MockNalChunkPeeker(5, true);
    try {
      peeker.peek(input, 10);
      Assert.assertEquals(0, input.getPeekPosition());
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }
}
