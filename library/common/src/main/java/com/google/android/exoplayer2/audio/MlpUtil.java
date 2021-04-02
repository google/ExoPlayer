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
import com.google.android.exoplayer2.util.ParsableByteArray;

/** Utility methods for parsing MLP frames, which are access units in MLP bitstreams. */
public final class MlpUtil {

  /** a MLP stream can carry simultaneously multiple representations of the same audio :
   * stereo as well as multichannel and object based immersive audio,
   * so just consider stereo by default */
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

  /**
   * The number of samples to store in each output chunk when rechunking TrueHD streams. The number
   * of samples extracted from the container corresponding to one syncframe must be an integer
   * multiple of this value.
   */
  public static final int TRUEHD_RECHUNK_SAMPLE_COUNT = 16;

  /**
   * Rechunks TrueHD sample data into groups of {@link #TRUEHD_RECHUNK_SAMPLE_COUNT} samples.
   */
  public static class TrueHdSampleRechunker {

    private int sampleCount;
    public long timeUs;
    public @C.BufferFlags int flags;
    public int sampleSize;

    public TrueHdSampleRechunker() {
      reset();
    }

    public void reset() {
      sampleCount = 0;
      sampleSize = 0;
    }

    /** Returns true when enough samples have been appended. */
    public boolean appendSampleMetadata(long timeUs, @C.BufferFlags int flags, int size) {

      if (sampleCount++ == 0) {
        // This is the first sample in the chunk.
        this.timeUs = timeUs;
        this.flags = flags;
        this.sampleSize = 0;
      }
      this.sampleSize += size;
      if (sampleCount >= TRUEHD_RECHUNK_SAMPLE_COUNT) {
        sampleCount = 0;
        return true;
      }
      return false;
    }
  }
}
