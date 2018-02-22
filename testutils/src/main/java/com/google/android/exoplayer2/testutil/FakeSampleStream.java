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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import java.io.IOException;

/**
 * Fake {@link SampleStream} that outputs a given {@link Format}, an optional sample containing a
 * single zero byte, then end of stream.
 */
public final class FakeSampleStream implements SampleStream {

  private final Format format;

  private boolean readFormat;
  private boolean readSample;

  public FakeSampleStream(Format format) {
    this(format, true);
  }

  public FakeSampleStream(Format format, boolean shouldOutputSample) {
    this.format = format;
    readSample = !shouldOutputSample;
  }

  @Override
  public boolean isReady() {
    return true;
  }

  @Override
  public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
      boolean formatRequired) {
    if (formatRequired || !readFormat) {
      formatHolder.format = format;
      readFormat = true;
      return C.RESULT_FORMAT_READ;
    } else if (!readSample) {
      buffer.timeUs = 0;
      buffer.ensureSpaceForWrite(1);
      buffer.data.put((byte) 0);
      buffer.flip();
      readSample = true;
      return C.RESULT_BUFFER_READ;
    } else {
      buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
      return C.RESULT_BUFFER_READ;
    }
  }

  @Override
  public void maybeThrowError() throws IOException {
    // Do nothing.
  }

  @Override
  public int skipData(long positionUs) {
    return 0;
  }

}
