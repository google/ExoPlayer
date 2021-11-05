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
package com.google.android.exoplayer2.ext.opus;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link OpusDecoder}. */
@RunWith(AndroidJUnit4.class)
public final class OpusDecoderTest {

  private static final byte[] HEADER =
      new byte[] {79, 112, 117, 115, 72, 101, 97, 100, 0, 2, 1, 56, 0, 0, -69, -128, 0, 0, 0};

  private static final int HEADER_PRE_SKIP_SAMPLES = 14337;

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;

  private static final ImmutableList<byte[]> HEADER_ONLY_INITIALIZATION_DATA =
      ImmutableList.of(HEADER);

  private static final long CUSTOM_PRE_SKIP_SAMPLES = 28674;
  private static final byte[] CUSTOM_PRE_SKIP_BYTES =
      buildNativeOrderByteArray(sampleCountToNanoseconds(CUSTOM_PRE_SKIP_SAMPLES));

  private static final long CUSTOM_SEEK_PRE_ROLL_SAMPLES = 7680;
  private static final byte[] CUSTOM_SEEK_PRE_ROLL_BYTES =
      buildNativeOrderByteArray(sampleCountToNanoseconds(CUSTOM_SEEK_PRE_ROLL_SAMPLES));

  private static final ImmutableList<byte[]> FULL_INITIALIZATION_DATA =
      ImmutableList.of(HEADER, CUSTOM_PRE_SKIP_BYTES, CUSTOM_SEEK_PRE_ROLL_BYTES);

  @Test
  public void getChannelCount() {
    int channelCount = OpusDecoder.getChannelCount(HEADER);
    assertThat(channelCount).isEqualTo(2);
  }

  @Test
  public void getPreSkipSamples_fullInitializationData_returnsOverrideValue() {
    int preSkipSamples = OpusDecoder.getPreSkipSamples(FULL_INITIALIZATION_DATA);
    assertThat(preSkipSamples).isEqualTo(CUSTOM_PRE_SKIP_SAMPLES);
  }

  @Test
  public void getPreSkipSamples_headerOnlyInitializationData_returnsHeaderValue() {
    int preSkipSamples = OpusDecoder.getPreSkipSamples(HEADER_ONLY_INITIALIZATION_DATA);
    assertThat(preSkipSamples).isEqualTo(HEADER_PRE_SKIP_SAMPLES);
  }

  @Test
  public void getSeekPreRollSamples_fullInitializationData_returnsInitializationDataValue() {
    int seekPreRollSamples = OpusDecoder.getSeekPreRollSamples(FULL_INITIALIZATION_DATA);
    assertThat(seekPreRollSamples).isEqualTo(CUSTOM_SEEK_PRE_ROLL_SAMPLES);
  }

  @Test
  public void getSeekPreRollSamples_headerOnlyInitializationData_returnsDefaultValue() {
    int seekPreRollSamples = OpusDecoder.getSeekPreRollSamples(HEADER_ONLY_INITIALIZATION_DATA);
    assertThat(seekPreRollSamples).isEqualTo(DEFAULT_SEEK_PRE_ROLL_SAMPLES);
  }

  private static long sampleCountToNanoseconds(long sampleCount) {
    return (sampleCount * C.NANOS_PER_SECOND) / OpusDecoder.SAMPLE_RATE;
  }

  private static byte[] buildNativeOrderByteArray(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
  }
}
