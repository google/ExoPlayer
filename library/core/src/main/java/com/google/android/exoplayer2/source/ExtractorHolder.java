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
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;

/** Stores a list of extractors and a selected extractor when the format has been detected. */
/* package */ final class ExtractorHolder {

  private final Extractor[] extractors;

  @Nullable private Extractor extractor;

  /**
   * Creates a holder that will select an extractor and initialize it using the specified output.
   *
   * @param extractors One or more extractors to choose from.
   */
  public ExtractorHolder(Extractor[] extractors) {
    this.extractors = extractors;
  }

  /**
   * Returns an initialized extractor for reading {@code input}, and returns the same extractor on
   * later calls.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param output The {@link ExtractorOutput} that will be used to initialize the selected
   *     extractor.
   * @param uri The {@link Uri} of the data.
   * @return An initialized extractor for reading {@code input}.
   * @throws UnrecognizedInputFormatException Thrown if the input format could not be detected.
   * @throws IOException Thrown if the input could not be read.
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  public Extractor selectExtractor(ExtractorInput input, ExtractorOutput output, Uri uri)
      throws IOException, InterruptedException {
    if (extractor != null) {
      return extractor;
    }
    if (extractors.length == 1) {
      this.extractor = extractors[0];
    } else {
      for (Extractor extractor : extractors) {
        try {
          if (extractor.sniff(input)) {
            this.extractor = extractor;
            break;
          }
        } catch (EOFException e) {
          // Do nothing.
        } finally {
          input.resetPeekPosition();
        }
      }
      if (extractor == null) {
        throw new UnrecognizedInputFormatException(
            "None of the available extractors ("
                + Util.getCommaDelimitedSimpleClassNames(extractors)
                + ") could read the stream.",
            uri);
      }
    }
    extractor.init(output);
    return extractor;
  }

  public void release() {
    if (extractor != null) {
      extractor.release();
      extractor = null;
    }
  }
}
