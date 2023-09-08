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
package androidx.media3.extractor.webp;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SingleSampleExtractor;
import java.io.IOException;

/** Extracts data from the WEBP container format. */
@UnstableApi
public final class WebpExtractor implements Extractor {

  // Documentation reference:
  // https://developers.google.com/speed/webp/docs/riff_container#webp_file_header
  private static final int FILE_SIGNATURE_SEGMENT_LENGTH = 4;
  private static final int RIFF_FILE_SIGNATURE = 0x52494646;
  private static final int WEBP_FILE_SIGNATURE = 0x57454250;

  private final ParsableByteArray scratch;
  private final SingleSampleExtractor imageExtractor;

  /** Creates an instance. */
  public WebpExtractor() {
    scratch = new ParsableByteArray(FILE_SIGNATURE_SEGMENT_LENGTH);
    imageExtractor = new SingleSampleExtractor(C.INDEX_UNSET, C.LENGTH_UNSET, MimeTypes.IMAGE_WEBP);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // The full file signature for the format webp is RIFF????WEBP.
    scratch.reset(/* limit= */ FILE_SIGNATURE_SEGMENT_LENGTH);
    input.peekFully(scratch.getData(), /* offset= */ 0, FILE_SIGNATURE_SEGMENT_LENGTH);
    if (scratch.readUnsignedInt() != RIFF_FILE_SIGNATURE) {
      return false;
    }

    input.advancePeekPosition(FILE_SIGNATURE_SEGMENT_LENGTH);
    scratch.reset(/* limit= */ FILE_SIGNATURE_SEGMENT_LENGTH);

    input.peekFully(scratch.getData(), /* offset= */ 0, FILE_SIGNATURE_SEGMENT_LENGTH);
    return scratch.readUnsignedInt() == WEBP_FILE_SIGNATURE;
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
