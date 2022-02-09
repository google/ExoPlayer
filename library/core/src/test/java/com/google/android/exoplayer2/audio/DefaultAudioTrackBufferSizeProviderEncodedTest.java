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

import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_PASSTHROUGH;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

/**
 * Tests for {@link DefaultAudioTrackBufferSizeProvider} for encoded audio except {@link
 * C#ENCODING_AC3}.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class DefaultAudioTrackBufferSizeProviderEncodedTest {

  private static final DefaultAudioTrackBufferSizeProvider DEFAULT =
      new DefaultAudioTrackBufferSizeProvider.Builder().build();

  @ParameterizedRobolectricTestRunner.Parameter(0)
  public @C.Encoding int encoding;

  @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: encoding={0}")
  public static ImmutableList<Integer> data() {
    return ImmutableList.of(
        C.ENCODING_MP3,
        C.ENCODING_AAC_LC,
        C.ENCODING_AAC_HE_V1,
        C.ENCODING_AC4,
        C.ENCODING_DTS,
        C.ENCODING_DOLBY_TRUEHD);
  }

  @Test
  public void getBufferSizeInBytes_veryBigMinBufferSize_isMinBufferSize() {
    int bufferSize =
        DEFAULT.getBufferSizeInBytes(
            /* minBufferSizeInBytes= */ 123456789,
            /* encoding= */ encoding,
            /* outputMode= */ OUTPUT_MODE_PASSTHROUGH,
            /* pcmFrameSize= */ 1,
            /* sampleRate= */ 0,
            /* maxAudioTrackPlaybackSpeed= */ 0);

    assertThat(bufferSize).isEqualTo(123456789);
  }
}
