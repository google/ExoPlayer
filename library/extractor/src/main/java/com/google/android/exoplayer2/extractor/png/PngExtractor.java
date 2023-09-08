/*
 * Copyright 2023 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.png;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SingleSampleExtractor;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;

/**
 * Extracts data from the PNG container format.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class PngExtractor implements Extractor {

  // See PNG (Portable Network Graphics) Specification, Version 1.2, Section 12.12 and Section 3.1.
  private static final int PNG_FILE_SIGNATURE = 0x8950;
  private static final int PNG_FILE_SIGNATURE_LENGTH = 2;

  private final SingleSampleExtractor imageExtractor;

  /** Creates an instance. */
  public PngExtractor() {
    imageExtractor =
        new SingleSampleExtractor(
            PNG_FILE_SIGNATURE, PNG_FILE_SIGNATURE_LENGTH, MimeTypes.IMAGE_PNG);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return imageExtractor.sniff(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    imageExtractor.init(output);
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    return imageExtractor.read(input, seekPosition);
  }

  @Override
  public void seek(long position, long timeUs) {
    imageExtractor.seek(position, timeUs);
  }

  @Override
  public void release() {
    // Do nothing.
  }
}
