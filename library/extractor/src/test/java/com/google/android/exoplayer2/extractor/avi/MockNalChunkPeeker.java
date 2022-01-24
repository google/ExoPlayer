package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;

public class MockNalChunkPeeker extends NalChunkPeeker {
  private boolean skip;
  public MockNalChunkPeeker(int peakSize, boolean skip) {
    super(peakSize);
    this.skip = skip;
  }

  @Override
  void processChunk(ExtractorInput input, int nalTypeOffset) throws IOException {

  }

  @Override
  boolean skip(byte nalType) {
    return skip;
  }
}
