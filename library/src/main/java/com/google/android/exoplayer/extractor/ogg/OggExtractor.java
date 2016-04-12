/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.ogg;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;

/**
 * Abstract Ogg {@link Extractor}.
 */
public abstract class OggExtractor implements Extractor {

  protected final ParsableByteArray scratch = new ParsableByteArray(
      new byte[OggReader.OGG_MAX_SEGMENT_SIZE * 255], 0);

  protected final OggReader oggReader = new OggReader();

  protected TrackOutput trackOutput;

  protected ExtractorOutput extractorOutput;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    try {
      OggUtil.PageHeader header = new OggUtil.PageHeader();
      if (!OggUtil.populatePageHeader(input, header, scratch, true)
          || (header.type & 0x02) != 0x02 || header.bodySize < 7) {
        return false;
      }
      scratch.reset();
      input.peekFully(scratch.data, 0, 7);
      return verifyBitstreamType();
    } catch (ParserException e) {
      // does not happen
    } finally {
      scratch.reset();
    }
    return false;
  }

  @Override
  public void init(ExtractorOutput output) {
    trackOutput = output.track(0);
    output.endTracks();
    extractorOutput = output;
  }

  @Override
  public void seek() {
    oggReader.reset();
    scratch.reset();
  }

  @Override
  public void release() {
    // Do nothing
  }

  protected abstract boolean verifyBitstreamType() throws ParserException;

}
