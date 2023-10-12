/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.android.exoplayer2.C.MICROS_PER_SECOND;
import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_PASSTHROUGH;
import static com.google.android.exoplayer2.audio.DefaultAudioTrackBufferSizeProvider.getMaximumEncodedRateBytesPerSecond;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link DefaultAudioTrackBufferSizeProvider} DTS-HD (DTS Express) audio. */
@RunWith(AndroidJUnit4.class)
public class DefaultAudioTrackBufferSizeProviderDTSHDTest {

  private static final DefaultAudioTrackBufferSizeProvider DEFAULT =
      new DefaultAudioTrackBufferSizeProvider.Builder().build();

  @Test
  public void
      getBufferSizeInBytes_passthroughDtshdAndNoBitrate_assumesMaxByteRateTimesMultiplicationFactor() {
    int bufferSize =
        DEFAULT.getBufferSizeInBytes(
            /* minBufferSizeInBytes= */ 0,
            /* encoding= */ C.ENCODING_DTS_HD,
            /* outputMode= */ OUTPUT_MODE_PASSTHROUGH,
            /* pcmFrameSize= */ 1,
            /* sampleRate= */ 0,
            /* bitrate= */ Format.NO_VALUE,
            /* maxAudioTrackPlaybackSpeed= */ 1);

    assertThat(bufferSize)
        .isEqualTo(
            durationUsToDtshdMaxBytes(DEFAULT.passthroughBufferDurationUs)
                * DEFAULT.dtshdBufferMultiplicationFactor);
  }

  @Test
  public void
      getBufferSizeInBytes_passthroughDtshdAt384Kbits_isPassthroughBufferSizeTimesMultiplicationFactor() {
    int bufferSize =
        DEFAULT.getBufferSizeInBytes(
            /* minBufferSizeInBytes= */ 0,
            /* encoding= */ C.ENCODING_DTS_HD,
            /* outputMode= */ OUTPUT_MODE_PASSTHROUGH,
            /* pcmFrameSize= */ 1,
            /* sampleRate= */ 0,
            /* bitrate= */ 384_000,
            /* maxAudioTrackPlaybackSpeed= */ 1);

    // Default buffer duration 0.25s => 0.25 * 384000 / 8 = 12000
    assertThat(bufferSize).isEqualTo(12000 * DEFAULT.dtshdBufferMultiplicationFactor);
  }

  private static int durationUsToDtshdMaxBytes(long durationUs) {
    return (int)
        (durationUs * getMaximumEncodedRateBytesPerSecond(C.ENCODING_DTS_HD) / MICROS_PER_SECOND);
  }
}
