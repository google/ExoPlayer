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
package com.google.android.exoplayer2.extractor.ts;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.text.cea.CeaUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Consumes SEI buffers, outputting contained CEA-608 messages to a {@link TrackOutput}.
 */
/* package */ final class SeiReader {

  private final TrackOutput output;

  public SeiReader(TrackOutput output) {
    this.output = output;
    output.format(Format.createTextSampleFormat(null, MimeTypes.APPLICATION_CEA608, null,
        Format.NO_VALUE, 0, null, null));
  }

  public void consume(long pesTimeUs, ParsableByteArray seiBuffer) {
    CeaUtil.consume(pesTimeUs, seiBuffer, output);
  }

}
