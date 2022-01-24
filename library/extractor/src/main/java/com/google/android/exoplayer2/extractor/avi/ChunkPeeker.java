package com.google.android.exoplayer2.extractor.avi;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import java.io.IOException;

public interface ChunkPeeker {
  void peek(ExtractorInput input, final int size) throws IOException;
}
