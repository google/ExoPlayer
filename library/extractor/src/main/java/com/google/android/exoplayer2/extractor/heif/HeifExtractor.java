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
package com.google.android.exoplayer2.extractor.heif;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SingleSampleExtractor;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;

/**
 * Extracts data from the HEIF (.heic) container format.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class HeifExtractor implements Extractor {

  // Specification reference: ISO/IEC 23008-12:2022
  private static final int HEIF_FILE_SIGNATURE_PART_1 = 0x66747970;
  private static final int HEIF_FILE_SIGNATURE_PART_2 = 0x68656963;
  private static final int FILE_SIGNATURE_SEGMENT_LENGTH = 4;

  private final ParsableByteArray scratch;
  private final SingleSampleExtractor imageExtractor;

  /** Creates an instance. */
  public HeifExtractor() {
    scratch = new ParsableByteArray(FILE_SIGNATURE_SEGMENT_LENGTH);
    imageExtractor = new SingleSampleExtractor(C.INDEX_UNSET, C.LENGTH_UNSET, MimeTypes.IMAGE_HEIF);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    input.advancePeekPosition(4);
    return readAndCompareFourBytes(input, HEIF_FILE_SIGNATURE_PART_1)
        && readAndCompareFourBytes(input, HEIF_FILE_SIGNATURE_PART_2);
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

  private boolean readAndCompareFourBytes(ExtractorInput input, int bytesToCompare)
      throws IOException {
    scratch.reset(/* limit= */ FILE_SIGNATURE_SEGMENT_LENGTH);
    input.peekFully(scratch.getData(), /* offset= */ 0, FILE_SIGNATURE_SEGMENT_LENGTH);
    return scratch.readUnsignedInt() == bytesToCompare;
  }
}
