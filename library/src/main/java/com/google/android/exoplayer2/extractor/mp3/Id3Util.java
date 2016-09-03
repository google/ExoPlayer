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
package com.google.android.exoplayer2.extractor.mp3;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataDecoderException;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Utility for parsing ID3 version 2 metadata in MP3 files.
 */
/* package */ final class Id3Util {

  /**
   * The maximum valid length for metadata in bytes.
   */
  private static final int MAXIMUM_METADATA_SIZE = 3 * 1024 * 1024;

  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");

  /**
   * Peeks data from the input and parses ID3 metadata, including gapless playback information.
   *
   * @param input The {@link ExtractorInput} from which data should be peeked.
   * @return The metadata, if present, {@code null} otherwise.
   * @throws IOException If an error occurred peeking from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  public static Metadata parseId3(ExtractorInput input)
      throws IOException, InterruptedException {
    Metadata result = null;
    ParsableByteArray scratch = new ParsableByteArray(10);
    int peekedId3Bytes = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 10);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }

      int majorVersion = scratch.readUnsignedByte();
      int minorVersion = scratch.readUnsignedByte();
      int flags = scratch.readUnsignedByte();
      int length = scratch.readSynchSafeInt();
      int frameLength = length + 10;

      try {
        if (canParseMetadata(majorVersion, minorVersion, flags, length)) {
          input.resetPeekPosition();
          byte[] frame = new byte[frameLength];
          input.peekFully(frame, 0, frameLength);
          return new Id3Decoder().decode(frame, frameLength);
        } else {
          input.advancePeekPosition(length);
        }
      } catch (MetadataDecoderException e) {
        e.printStackTrace();
      }

      peekedId3Bytes += frameLength;
    }
    input.resetPeekPosition();
    input.advancePeekPosition(peekedId3Bytes);
    return result;
  }

  private static boolean canParseMetadata(int majorVersion, int minorVersion, int flags,
      int length) {
    return minorVersion != 0xFF && majorVersion >= 2 && majorVersion <= 4
        && length <= MAXIMUM_METADATA_SIZE
        && !(majorVersion == 2 && ((flags & 0x3F) != 0 || (flags & 0x40) != 0))
        && !(majorVersion == 3 && (flags & 0x1F) != 0)
        && !(majorVersion == 4 && (flags & 0x0F) != 0);
  }

  private Id3Util() {}

}
