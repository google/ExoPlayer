/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.audio;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.nio.ByteBuffer;

/** Utility methods for parsing MLP frames, which are access units in MLP bitstreams. */
public final class MlpUtil {

  /** The channel count of MLP stream. */
  // TODO: Parse MLP stream channel count.
  private static final int CHANNEL_COUNT_2 = 2;


  /**
   * Returns the MLP format given {@code data} containing the MLPSpecificBox according to
   * dolbytruehdbitstreamswithintheisobasemediafileformat.pdf
   * The reading position of {@code data} will be modified.
   *
   * @param data The MLPSpecificBox to parse.
   * @param trackId The track identifier to set on the format.
   * @param sampleRate The sample rate to be included in the format.
   * @param language The language to set on the format.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @return The MLP format parsed from data in the header.
   */
  public static Format parseMlpFormat(
      ParsableByteArray data, String trackId, int sampleRate,
      String language, @Nullable DrmInitData drmInitData) {

    return new Format.Builder()
        .setId(trackId)
        .setSampleMimeType(MimeTypes.AUDIO_TRUEHD)
        .setChannelCount(CHANNEL_COUNT_2)
        .setSampleRate(sampleRate)
        .setDrmInitData(drmInitData)
        .setLanguage(language)
        .build();
  }

  private MlpUtil() {}
}
