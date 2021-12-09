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

import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_PASSTHROUGH;
import static com.google.android.exoplayer2.audio.DefaultAudioSink.OUTPUT_MODE_PCM;
import static com.google.android.exoplayer2.audio.DefaultAudioTrackBufferSizeProvider.getMaximumEncodedRateBytesPerSecond;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;

/** Tests for {@link DefaultAudioTrackBufferSizeProvider}. */
@RunWith(JUnit4.class)
public class DefaultAudioTrackBufferSizeProviderTest {

  private static final DefaultAudioTrackBufferSizeProvider DEFAULT =
      new DefaultAudioTrackBufferSizeProvider.Builder().build();

  /** Tests for {@link DefaultAudioTrackBufferSizeProvider} for PCM audio. */
  @RunWith(Parameterized.class)
  public static class PcmTest {

    @Parameterized.Parameter(0)
    @C.PcmEncoding
    public int encoding;

    @Parameterized.Parameter(1)
    public int channelCount;

    @Parameterized.Parameter(2)
    public int sampleRate;

    @Parameterized.Parameters(name = "{index}: encoding={0}, channelCount={1}, sampleRate={2}")
    public static List<Integer[]> data() {
      return Sets.cartesianProduct(
              ImmutableList.of(
                  /* encoding */ ImmutableSet.of(
                      C.ENCODING_PCM_8BIT,
                      C.ENCODING_PCM_16BIT,
                      C.ENCODING_PCM_16BIT_BIG_ENDIAN,
                      C.ENCODING_PCM_24BIT,
                      C.ENCODING_PCM_32BIT,
                      C.ENCODING_PCM_FLOAT),
                  /* channelCount */ ImmutableSet.of(1, 2, 3, 4, 6, 8),
                  /* sampleRate*/ ImmutableSet.of(8000, 16000, 44100, 48000, 96000)))
          .stream()
          .map(s -> s.toArray(new Integer[0]))
          .collect(Collectors.toList());
    }

    private int getPcmFrameSize() {
      return Util.getPcmFrameSize(encoding, channelCount);
    }

    private int durationUsToBytes(int durationUs) {
      return (int) (((long) durationUs * getPcmFrameSize() * sampleRate) / C.MICROS_PER_SECOND);
    }

    @Test
    public void getBufferSizeInBytes_veryBigMinBufferSize_isMinBufferSize() {
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ 123456789,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1);

      assertThat(bufferSize).isEqualTo(123456789);
    }

    @Test
    public void getBufferSizeInBytes_noMinBufferSize_isMinBufferDuration() {
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ 0,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1);

      assertThat(bufferSize).isEqualTo(durationUsToBytes(DEFAULT.minPcmBufferDurationUs));
    }

    @Test
    public void getBufferSizeInBytes_tooSmallMinBufferSize_isMinBufferDuration() {
      int minBufferSizeInBytes =
          durationUsToBytes(DEFAULT.minPcmBufferDurationUs / DEFAULT.pcmBufferMultiplicationFactor)
              - 1;
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ minBufferSizeInBytes,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1);

      assertThat(bufferSize).isEqualTo(durationUsToBytes(DEFAULT.minPcmBufferDurationUs));
    }

    @Test
    public void getBufferSizeInBytes_lowMinBufferSize_multipliesAudioTrackMinBuffer() {
      int minBufferSizeInBytes =
          durationUsToBytes(DEFAULT.minPcmBufferDurationUs / DEFAULT.pcmBufferMultiplicationFactor)
              + 1;
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ minBufferSizeInBytes,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1);

      assertThat(bufferSize)
          .isEqualTo(minBufferSizeInBytes * DEFAULT.pcmBufferMultiplicationFactor);
    }

    @Test
    public void getBufferSizeInBytes_highMinBufferSize_multipliesAudioTrackMinBuffer() {
      int minBufferSizeInBytes =
          durationUsToBytes(DEFAULT.maxPcmBufferDurationUs / DEFAULT.pcmBufferMultiplicationFactor)
              - 1;
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ minBufferSizeInBytes,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1);

      assertThat(bufferSize)
          .isEqualTo(minBufferSizeInBytes * DEFAULT.pcmBufferMultiplicationFactor);
    }

    @Test
    public void getBufferSizeInBytes_tooHighMinBufferSize_isMaxBufferDuration() {
      int minBufferSizeInBytes =
          durationUsToBytes(DEFAULT.maxPcmBufferDurationUs / DEFAULT.pcmBufferMultiplicationFactor)
              + 1;
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ minBufferSizeInBytes,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1);

      assertThat(bufferSize).isEqualTo(durationUsToBytes(DEFAULT.maxPcmBufferDurationUs));
    }

    @Test
    public void getBufferSizeInBytes_lowPlaybackSpeed_isScaledByPlaybackSpeed() {
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ 0,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 1 / 5F);

      assertThat(bufferSize).isEqualTo(durationUsToBytes(DEFAULT.minPcmBufferDurationUs / 5));
    }

    @Test
    public void getBufferSizeInBytes_highPlaybackSpeed_isScaledByPlaybackSpeed() {
      int bufferSize =
          DEFAULT.getBufferSizeInBytes(
              /* minBufferSizeInBytes= */ 0,
              /* encoding= */ encoding,
              /* outputMode= */ OUTPUT_MODE_PCM,
              /* pcmFrameSize= */ getPcmFrameSize(),
              /* sampleRate= */ sampleRate,
              /* maxAudioTrackPlaybackSpeed= */ 5F);

      assertThat(bufferSize).isEqualTo(durationUsToBytes(DEFAULT.minPcmBufferDurationUs * 5));
    }
  }
  /**
   * Tests for {@link DefaultAudioTrackBufferSizeProvider} for encoded audio except {@link
   * C#ENCODING_AC3}.
   */
  @RunWith(Parameterized.class)
  public static class EncodedTest {

    @Parameterized.Parameter(0)
    @C.Encoding
    public int encoding;

    @Parameterized.Parameters(name = "{index}: encoding={0}")
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
              /* pcmFrameSize= */ C.LENGTH_UNSET,
              /* sampleRate= */ 0,
              /* maxAudioTrackPlaybackSpeed= */ 0);

      assertThat(bufferSize).isEqualTo(123456789);
    }
  }

  @Test
  public void
      getBufferSizeInBytes_passthroughAC3_isPassthroughBufferSizeTimesMultiplicationFactor() {
    int bufferSize =
        DEFAULT.getBufferSizeInBytes(
            /* minBufferSizeInBytes= */ 0,
            /* encoding= */ C.ENCODING_AC3,
            /* outputMode= */ OUTPUT_MODE_PASSTHROUGH,
            /* pcmFrameSize= */ C.LENGTH_UNSET,
            /* sampleRate= */ 0,
            /* maxAudioTrackPlaybackSpeed= */ 1);

    assertThat(bufferSize)
        .isEqualTo(
            durationUsToAc3MaxBytes(DEFAULT.passthroughBufferDurationUs)
                * DEFAULT.ac3BufferMultiplicationFactor);
  }

  private static int durationUsToAc3MaxBytes(long durationUs) {
    return (int)
        (durationUs * getMaximumEncodedRateBytesPerSecond(C.ENCODING_AC3) / C.MICROS_PER_SECOND);
  }
}
