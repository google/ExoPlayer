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
