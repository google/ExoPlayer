/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;

/**
 * {@link ProgressiveMediaExtractor} built on top of {@link Extractor} instances, whose
 * implementation classes are bundled in the app.
 */
/* package */ final class BundledExtractorsAdapter implements ProgressiveMediaExtractor {

  private final Extractor[] extractors;

  @Nullable private Extractor extractor;
  @Nullable private ExtractorInput extractorInput;

  /**
   * Creates a holder that will select an extractor and initialize it using the specified output.
   *
   * @param extractors One or more extractors to choose from.
   */
  public BundledExtractorsAdapter(Extractor[] extractors) {
    this.extractors = extractors;
  }

  @Override
  public void init(
      DataReader dataReader, Uri uri, long position, long length, ExtractorOutput output)
      throws IOException {
    extractorInput = new DefaultExtractorInput(dataReader, position, length);
    if (extractor != null) {
      return;
    }
    if (extractors.length == 1) {
      this.extractor = extractors[0];
    } else {
      for (Extractor extractor : extractors) {
        try {
          if (extractor.sniff(extractorInput)) {
            this.extractor = extractor;
            break;
          }
        } catch (EOFException e) {
          // Do nothing.
        } finally {
          extractorInput.resetPeekPosition();
        }
      }
      if (extractor == null) {
        throw new UnrecognizedInputFormatException(
            "None of the available extractors ("
                + Util.getCommaDelimitedSimpleClassNames(extractors)
                + ") could read the stream.",
            Assertions.checkNotNull(uri));
      }
    }
    extractor.init(output);
  }

  @Override
  public void release() {
    if (extractor != null) {
      extractor.release();
      extractor = null;
    }
    extractorInput = null;
  }

  @Override
  public void disableSeekingOnMp3Streams() {
    if (extractor instanceof Mp3Extractor) {
      ((Mp3Extractor) extractor).disableSeeking();
    }
  }

  @Override
  public long getCurrentInputPosition() {
    return extractorInput != null ? extractorInput.getPosition() : C.POSITION_UNSET;
  }

  @Override
  public void seek(long position, long seekTimeUs) {
    Assertions.checkNotNull(extractor).seek(position, seekTimeUs);
  }

  @Override
  public int read(PositionHolder positionHolder) throws IOException {
    return Assertions.checkNotNull(extractor)
        .read(Assertions.checkNotNull(extractorInput), positionHolder);
  }
}
