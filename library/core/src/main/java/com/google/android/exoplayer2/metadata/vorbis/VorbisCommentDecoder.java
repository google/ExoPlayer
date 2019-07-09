/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.metadata.vorbis;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import java.util.ArrayList;

/** Decodes vorbis comments */
public class VorbisCommentDecoder {

  private static final String SEPARATOR = "=";

  /**
   * Decodes an {@link ArrayList} of vorbis comments.
   *
   * @param metadataStringList An {@link ArrayList} containing vorbis comments as {@link String}
   * @return A {@link Metadata} structure with the vorbis comments as its entries.
   */
  public Metadata decodeVorbisComments(@Nullable ArrayList<String> metadataStringList) {
    if (metadataStringList == null || metadataStringList.size() == 0) {
      return null;
    }

    ArrayList<VorbisCommentFrame> vorbisCommentFrames = new ArrayList<>();
    VorbisCommentFrame vorbisCommentFrame;

    for (String commentEntry : metadataStringList) {
      String[] keyValue;

      keyValue = commentEntry.split(SEPARATOR);
      if (keyValue.length != 2) {
        /* Could not parse this comment, no key value pair found */
        continue;
      }
      vorbisCommentFrame = new VorbisCommentFrame(keyValue[0], keyValue[1]);
      vorbisCommentFrames.add(vorbisCommentFrame);
    }

    if (vorbisCommentFrames.size() > 0) {
      return new Metadata(vorbisCommentFrames);
    } else {
      return null;
    }
  }
}
