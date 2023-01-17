/*
 * Copyright (C) 2022 The Android Open Source Project
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

/** Tests for {@link DefaultAudioTrackBufferSizeProvider} AC3 audio. */
@RunWith(AndroidJUnit4.class)
public class DefaultAudioTrackBufferSizeProviderAC3Test {

  private static final DefaultAudioTrackBufferSizeProvider DEFAULT =
      new DefaultAudioTrackBufferSizeProvider.Builder().build();

  @Test
  public void
      getBufferSizeInBytes_passthroughAc3AndNoBitrate_assumesMaxByteRateTimesMultiplicationFactor() {
    int bufferSize =
        DEFAULT.getBufferSizeInBytes(
            /* minBufferSizeInBytes= */ 0,
            /* encoding= */ C.ENCODING_AC3,
            /* outputMode= */ OUTPUT_MODE_PASSTHROUGH,
            /* pcmFrameSize= */ 1,
            /* sampleRate= */ 0,
            /* bitrate= */ Format.NO_VALUE,
            /* maxAudioTrackPlaybackSpeed= */ 1);

    assertThat(bufferSize)
        .isEqualTo(
            durationUsToAc3MaxBytes(DEFAULT.passthroughBufferDurationUs)
                * DEFAULT.ac3BufferMultiplicationFactor);
  }

  @Test
  public void
      getBufferSizeInBytes_passthroughAC3At256Kbits_isPassthroughBufferSizeTimesMultiplicationFactor() {
    int bufferSize =
        DEFAULT.getBufferSizeInBytes(
            /* minBufferSizeInBytes= */ 0,
            /* encoding= */ C.ENCODING_AC3,
            /* outputMode= */ OUTPUT_MODE_PASSTHROUGH,
            /* pcmFrameSize= */ 1,
            /* sampleRate= */ 0,
            /* bitrate= */ 256_000,
            /* maxAudioTrackPlaybackSpeed= */ 1);

    // Default buffer duration 0.25s => 0.25 * 256000 / 8 = 8000
    assertThat(bufferSize).isEqualTo(8000 * DEFAULT.ac3BufferMultiplicationFactor);
  }

  private static int durationUsToAc3MaxBytes(long durationUs) {
    return (int)
        (durationUs * getMaximumEncodedRateBytesPerSecond(C.ENCODING_AC3) / MICROS_PER_SECOND);
  }
}
