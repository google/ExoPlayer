/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Util.getBytesFromHexString;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link OpusUtil}. */
@RunWith(AndroidJUnit4.class)
public final class OpusUtilTest {

  private static final byte[] HEADER =
      new byte[] {79, 112, 117, 115, 72, 101, 97, 100, 0, 2, 1, 56, 0, 0, -69, -128, 0, 0, 0};

  private static final int HEADER_PRE_SKIP_SAMPLES = 14337;
  private static final byte[] HEADER_PRE_SKIP_BYTES =
      buildNativeOrderByteArray(sampleCountToNanoseconds(HEADER_PRE_SKIP_SAMPLES));

  private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;
  private static final byte[] DEFAULT_SEEK_PRE_ROLL_BYTES =
      buildNativeOrderByteArray(sampleCountToNanoseconds(DEFAULT_SEEK_PRE_ROLL_SAMPLES));

  @Test
  public void buildInitializationData_returnsExpectedHeaderWithPreSkipAndPreRoll() {
    List<byte[]> initializationData = OpusUtil.buildInitializationData(HEADER);

    assertThat(initializationData).hasSize(3);
    assertThat(initializationData.get(0)).isEqualTo(HEADER);
    assertThat(initializationData.get(1)).isEqualTo(HEADER_PRE_SKIP_BYTES);
    assertThat(initializationData.get(2)).isEqualTo(DEFAULT_SEEK_PRE_ROLL_BYTES);
  }

  @Test
  public void getChannelCount_returnsChannelCount() {
    int channelCount = OpusUtil.getChannelCount(HEADER);

    assertThat(channelCount).isEqualTo(2);
  }

  @Test
  public void getPacketDurationUs_code0_returnsExpectedDuration() {
    long config0DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("04"));
    long config1DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("0C"));
    long config2DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("14"));
    long config3DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("1C"));
    long config4DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("24"));
    long config5DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("2C"));
    long config6DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("34"));
    long config7DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("3C"));
    long config8DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("44"));
    long config9DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("4C"));
    long config10DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("54"));
    long config11DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("5C"));
    long config12DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("64"));
    long config13DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("6C"));
    long config14DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("74"));
    long config15DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("7C"));
    long config16DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("84"));
    long config17DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("8C"));
    long config18DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("94"));
    long config19DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("9C"));
    long config20DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("A4"));
    long config21DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("AC"));
    long config22DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("B4"));
    long config23DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("BC"));
    long config24DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("C4"));
    long config25DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("CC"));
    long config26DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("D4"));
    long config27DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("DC"));
    long config28DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("E4"));
    long config29DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("EC"));
    long config30DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("F4"));
    long config31DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("FC"));

    assertThat(config0DurationUs).isEqualTo(10_000);
    assertThat(config1DurationUs).isEqualTo(20_000);
    assertThat(config2DurationUs).isEqualTo(40_000);
    assertThat(config3DurationUs).isEqualTo(60_000);
    assertThat(config4DurationUs).isEqualTo(10_000);
    assertThat(config5DurationUs).isEqualTo(20_000);
    assertThat(config6DurationUs).isEqualTo(40_000);
    assertThat(config7DurationUs).isEqualTo(60_000);
    assertThat(config8DurationUs).isEqualTo(10_000);
    assertThat(config9DurationUs).isEqualTo(20_000);
    assertThat(config10DurationUs).isEqualTo(40_000);
    assertThat(config11DurationUs).isEqualTo(60_000);
    assertThat(config12DurationUs).isEqualTo(10_000);
    assertThat(config13DurationUs).isEqualTo(20_000);
    assertThat(config14DurationUs).isEqualTo(10_000);
    assertThat(config15DurationUs).isEqualTo(20_000);
    assertThat(config16DurationUs).isEqualTo(2_500);
    assertThat(config17DurationUs).isEqualTo(5_000);
    assertThat(config18DurationUs).isEqualTo(10_000);
    assertThat(config19DurationUs).isEqualTo(20_000);
    assertThat(config20DurationUs).isEqualTo(2_500);
    assertThat(config21DurationUs).isEqualTo(5_000);
    assertThat(config22DurationUs).isEqualTo(10_000);
    assertThat(config23DurationUs).isEqualTo(20_000);
    assertThat(config24DurationUs).isEqualTo(2_500);
    assertThat(config25DurationUs).isEqualTo(5_000);
    assertThat(config26DurationUs).isEqualTo(10_000);
    assertThat(config27DurationUs).isEqualTo(20_000);
    assertThat(config28DurationUs).isEqualTo(2_500);
    assertThat(config29DurationUs).isEqualTo(5_000);
    assertThat(config30DurationUs).isEqualTo(10_000);
    assertThat(config31DurationUs).isEqualTo(20_000);
  }

  @Test
  public void getPacketDurationUs_code1_returnsExpectedDuration() {
    long config0DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("05"));
    long config1DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("0D"));
    long config2DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("15"));
    long config3DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("1D"));
    long config4DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("25"));
    long config5DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("2D"));
    long config6DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("35"));
    long config7DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("3D"));
    long config8DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("45"));
    long config9DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("4D"));
    long config10DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("55"));
    long config11DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("5D"));
    long config12DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("65"));
    long config13DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("6D"));
    long config14DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("75"));
    long config15DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("7D"));
    long config16DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("85"));
    long config17DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("8D"));
    long config18DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("95"));
    long config19DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("9D"));
    long config20DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("A5"));
    long config21DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("AD"));
    long config22DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("B5"));
    long config23DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("BD"));
    long config24DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("C5"));
    long config25DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("CD"));
    long config26DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("D5"));
    long config27DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("DD"));
    long config28DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("E5"));
    long config29DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("ED"));
    long config30DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("F5"));
    long config31DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("FD"));

    assertThat(config0DurationUs).isEqualTo(20_000);
    assertThat(config1DurationUs).isEqualTo(40_000);
    assertThat(config2DurationUs).isEqualTo(80_000);
    assertThat(config3DurationUs).isEqualTo(120_000);
    assertThat(config4DurationUs).isEqualTo(20_000);
    assertThat(config5DurationUs).isEqualTo(40_000);
    assertThat(config6DurationUs).isEqualTo(80_000);
    assertThat(config7DurationUs).isEqualTo(120_000);
    assertThat(config8DurationUs).isEqualTo(20_000);
    assertThat(config9DurationUs).isEqualTo(40_000);
    assertThat(config10DurationUs).isEqualTo(80_000);
    assertThat(config11DurationUs).isEqualTo(120_000);
    assertThat(config12DurationUs).isEqualTo(20_000);
    assertThat(config13DurationUs).isEqualTo(40_000);
    assertThat(config14DurationUs).isEqualTo(20_000);
    assertThat(config15DurationUs).isEqualTo(40_000);
    assertThat(config16DurationUs).isEqualTo(5_000);
    assertThat(config17DurationUs).isEqualTo(10_000);
    assertThat(config18DurationUs).isEqualTo(20_000);
    assertThat(config19DurationUs).isEqualTo(40_000);
    assertThat(config20DurationUs).isEqualTo(5_000);
    assertThat(config21DurationUs).isEqualTo(10_000);
    assertThat(config22DurationUs).isEqualTo(20_000);
    assertThat(config23DurationUs).isEqualTo(40_000);
    assertThat(config24DurationUs).isEqualTo(5_000);
    assertThat(config25DurationUs).isEqualTo(10_000);
    assertThat(config26DurationUs).isEqualTo(20_000);
    assertThat(config27DurationUs).isEqualTo(40_000);
    assertThat(config28DurationUs).isEqualTo(5_000);
    assertThat(config29DurationUs).isEqualTo(10_000);
    assertThat(config30DurationUs).isEqualTo(20_000);
    assertThat(config31DurationUs).isEqualTo(40_000);
  }

  @Test
  public void getPacketDurationUs_code2_returnsExpectedDuration() {
    long config0DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("06"));
    long config1DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("0E"));
    long config2DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("16"));
    long config3DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("1E"));
    long config4DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("26"));
    long config5DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("2E"));
    long config6DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("36"));
    long config7DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("3E"));
    long config8DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("46"));
    long config9DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("4E"));
    long config10DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("56"));
    long config11DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("5E"));
    long config12DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("66"));
    long config13DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("6E"));
    long config14DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("76"));
    long config15DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("7E"));
    long config16DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("86"));
    long config17DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("8E"));
    long config18DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("96"));
    long config19DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("9E"));
    long config20DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("A6"));
    long config21DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("AE"));
    long config22DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("B6"));
    long config23DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("BE"));
    long config24DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("C6"));
    long config25DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("CE"));
    long config26DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("D6"));
    long config27DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("DE"));
    long config28DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("E6"));
    long config29DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("EE"));
    long config30DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("F6"));
    long config31DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("FE"));

    assertThat(config0DurationUs).isEqualTo(20_000);
    assertThat(config1DurationUs).isEqualTo(40_000);
    assertThat(config2DurationUs).isEqualTo(80_000);
    assertThat(config3DurationUs).isEqualTo(120_000);
    assertThat(config4DurationUs).isEqualTo(20_000);
    assertThat(config5DurationUs).isEqualTo(40_000);
    assertThat(config6DurationUs).isEqualTo(80_000);
    assertThat(config7DurationUs).isEqualTo(120_000);
    assertThat(config8DurationUs).isEqualTo(20_000);
    assertThat(config9DurationUs).isEqualTo(40_000);
    assertThat(config10DurationUs).isEqualTo(80_000);
    assertThat(config11DurationUs).isEqualTo(120_000);
    assertThat(config12DurationUs).isEqualTo(20_000);
    assertThat(config13DurationUs).isEqualTo(40_000);
    assertThat(config14DurationUs).isEqualTo(20_000);
    assertThat(config15DurationUs).isEqualTo(40_000);
    assertThat(config16DurationUs).isEqualTo(5_000);
    assertThat(config17DurationUs).isEqualTo(10_000);
    assertThat(config18DurationUs).isEqualTo(20_000);
    assertThat(config19DurationUs).isEqualTo(40_000);
    assertThat(config20DurationUs).isEqualTo(5_000);
    assertThat(config21DurationUs).isEqualTo(10_000);
    assertThat(config22DurationUs).isEqualTo(20_000);
    assertThat(config23DurationUs).isEqualTo(40_000);
    assertThat(config24DurationUs).isEqualTo(5_000);
    assertThat(config25DurationUs).isEqualTo(10_000);
    assertThat(config26DurationUs).isEqualTo(20_000);
    assertThat(config27DurationUs).isEqualTo(40_000);
    assertThat(config28DurationUs).isEqualTo(5_000);
    assertThat(config29DurationUs).isEqualTo(10_000);
    assertThat(config30DurationUs).isEqualTo(20_000);
    assertThat(config31DurationUs).isEqualTo(40_000);
  }

  @Test
  public void getPacketDurationUs_code3_returnsExpectedDuration() {
    // max possible frame count to reach 120ms duration
    long config0DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("078C"));
    long config1DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("0F86"));
    long config2DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("1783"));
    long config3DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("1F82"));
    // frame count of 2
    long config4DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("2782"));
    long config5DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("2F82"));
    long config6DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("3782"));
    long config7DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("3F82"));
    long config8DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("4782"));
    long config9DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("4F82"));
    long config10DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("5782"));
    long config11DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("5F82"));
    // max possible frame count to reach 120ms duration
    long config12DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("678C"));
    long config13DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("6F86"));
    // frame count of 2
    long config14DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("7782"));
    long config15DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("7F82"));
    // max possible frame count to reach 120ms duration
    long config16DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("87B0"));
    long config17DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("8F98"));
    long config18DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("978C"));
    long config19DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("9F86"));
    // frame count of 2
    long config20DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("A782"));
    long config21DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("AF82"));
    long config22DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("B782"));
    long config23DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("BF82"));
    long config24DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("C782"));
    long config25DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("CF82"));
    long config26DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("D782"));
    long config27DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("DF82"));
    long config28DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("E782"));
    long config29DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("EF82"));
    long config30DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("F782"));
    long config31DurationUs = OpusUtil.getPacketDurationUs(getBytesFromHexString("FF82"));

    assertThat(config0DurationUs).isEqualTo(120_000);
    assertThat(config1DurationUs).isEqualTo(120_000);
    assertThat(config2DurationUs).isEqualTo(120_000);
    assertThat(config3DurationUs).isEqualTo(120_000);
    assertThat(config4DurationUs).isEqualTo(20_000);
    assertThat(config5DurationUs).isEqualTo(40_000);
    assertThat(config6DurationUs).isEqualTo(80_000);
    assertThat(config7DurationUs).isEqualTo(120_000);
    assertThat(config8DurationUs).isEqualTo(20_000);
    assertThat(config9DurationUs).isEqualTo(40_000);
    assertThat(config10DurationUs).isEqualTo(80_000);
    assertThat(config11DurationUs).isEqualTo(120_000);
    assertThat(config12DurationUs).isEqualTo(120_000);
    assertThat(config13DurationUs).isEqualTo(120_000);
    assertThat(config14DurationUs).isEqualTo(20_000);
    assertThat(config15DurationUs).isEqualTo(40_000);
    assertThat(config16DurationUs).isEqualTo(120_000);
    assertThat(config17DurationUs).isEqualTo(120_000);
    assertThat(config18DurationUs).isEqualTo(120_000);
    assertThat(config19DurationUs).isEqualTo(120_000);
    assertThat(config20DurationUs).isEqualTo(5_000);
    assertThat(config21DurationUs).isEqualTo(10_000);
    assertThat(config22DurationUs).isEqualTo(20_000);
    assertThat(config23DurationUs).isEqualTo(40_000);
    assertThat(config24DurationUs).isEqualTo(5_000);
    assertThat(config25DurationUs).isEqualTo(10_000);
    assertThat(config26DurationUs).isEqualTo(20_000);
    assertThat(config27DurationUs).isEqualTo(40_000);
    assertThat(config28DurationUs).isEqualTo(5_000);
    assertThat(config29DurationUs).isEqualTo(10_000);
    assertThat(config30DurationUs).isEqualTo(20_000);
    assertThat(config31DurationUs).isEqualTo(40_000);
  }

  @Test
  public void getPacketAudioSampleCount_code0_returnsExpectedDuration() {
    int config0SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("04"));
    int config1SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("0C"));
    int config2SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("14"));
    int config3SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("1C"));
    int config4SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("24"));
    int config5SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("2C"));
    int config6SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("34"));
    int config7SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("3C"));
    int config8SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("44"));
    int config9SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("4C"));
    int config10SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("54"));
    int config11SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("5C"));
    int config12SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("64"));
    int config13SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("6C"));
    int config14SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("74"));
    int config15SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("7C"));
    int config16SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("84"));
    int config17SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("8C"));
    int config18SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("94"));
    int config19SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("9C"));
    int config20SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("A4"));
    int config21SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("AC"));
    int config22SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("B4"));
    int config23SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("BC"));
    int config24SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("C4"));
    int config25SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("CC"));
    int config26SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("D4"));
    int config27SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("DC"));
    int config28SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("E4"));
    int config29SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("EC"));
    int config30SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("F4"));
    int config31SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("FC"));

    assertThat(config0SampleCount).isEqualTo(480);
    assertThat(config1SampleCount).isEqualTo(960);
    assertThat(config2SampleCount).isEqualTo(1920);
    assertThat(config3SampleCount).isEqualTo(2880);
    assertThat(config4SampleCount).isEqualTo(480);
    assertThat(config5SampleCount).isEqualTo(960);
    assertThat(config6SampleCount).isEqualTo(1920);
    assertThat(config7SampleCount).isEqualTo(2880);
    assertThat(config8SampleCount).isEqualTo(480);
    assertThat(config9SampleCount).isEqualTo(960);
    assertThat(config10SampleCount).isEqualTo(1920);
    assertThat(config11SampleCount).isEqualTo(2880);
    assertThat(config12SampleCount).isEqualTo(480);
    assertThat(config13SampleCount).isEqualTo(960);
    assertThat(config14SampleCount).isEqualTo(480);
    assertThat(config15SampleCount).isEqualTo(960);
    assertThat(config16SampleCount).isEqualTo(120);
    assertThat(config17SampleCount).isEqualTo(240);
    assertThat(config18SampleCount).isEqualTo(480);
    assertThat(config19SampleCount).isEqualTo(960);
    assertThat(config20SampleCount).isEqualTo(120);
    assertThat(config21SampleCount).isEqualTo(240);
    assertThat(config22SampleCount).isEqualTo(480);
    assertThat(config23SampleCount).isEqualTo(960);
    assertThat(config24SampleCount).isEqualTo(120);
    assertThat(config25SampleCount).isEqualTo(240);
    assertThat(config26SampleCount).isEqualTo(480);
    assertThat(config27SampleCount).isEqualTo(960);
    assertThat(config28SampleCount).isEqualTo(120);
    assertThat(config29SampleCount).isEqualTo(240);
    assertThat(config30SampleCount).isEqualTo(480);
    assertThat(config31SampleCount).isEqualTo(960);
  }

  @Test
  public void getPacketAudioSampleCount_code1_returnsExpectedDuration() {
    int config0SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("05"));
    int config1SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("0D"));
    int config2SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("15"));
    int config3SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("1D"));
    int config4SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("25"));
    int config5SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("2D"));
    int config6SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("35"));
    int config7SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("3D"));
    int config8SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("45"));
    int config9SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("4D"));
    int config10SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("55"));
    int config11SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("5D"));
    int config12SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("65"));
    int config13SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("6D"));
    int config14SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("75"));
    int config15SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("7D"));
    int config16SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("85"));
    int config17SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("8D"));
    int config18SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("95"));
    int config19SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("9D"));
    int config20SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("A5"));
    int config21SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("AD"));
    int config22SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("B5"));
    int config23SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("BD"));
    int config24SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("C5"));
    int config25SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("CD"));
    int config26SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("D5"));
    int config27SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("DD"));
    int config28SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("E5"));
    int config29SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("ED"));
    int config30SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("F5"));
    int config31SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("FD"));

    assertThat(config0SampleCount).isEqualTo(960);
    assertThat(config1SampleCount).isEqualTo(1920);
    assertThat(config2SampleCount).isEqualTo(3840);
    assertThat(config3SampleCount).isEqualTo(5760);
    assertThat(config4SampleCount).isEqualTo(960);
    assertThat(config5SampleCount).isEqualTo(1920);
    assertThat(config6SampleCount).isEqualTo(3840);
    assertThat(config7SampleCount).isEqualTo(5760);
    assertThat(config8SampleCount).isEqualTo(960);
    assertThat(config9SampleCount).isEqualTo(1920);
    assertThat(config10SampleCount).isEqualTo(3840);
    assertThat(config11SampleCount).isEqualTo(5760);
    assertThat(config12SampleCount).isEqualTo(960);
    assertThat(config13SampleCount).isEqualTo(1920);
    assertThat(config14SampleCount).isEqualTo(960);
    assertThat(config15SampleCount).isEqualTo(1920);
    assertThat(config16SampleCount).isEqualTo(240);
    assertThat(config17SampleCount).isEqualTo(480);
    assertThat(config18SampleCount).isEqualTo(960);
    assertThat(config19SampleCount).isEqualTo(1920);
    assertThat(config20SampleCount).isEqualTo(240);
    assertThat(config21SampleCount).isEqualTo(480);
    assertThat(config22SampleCount).isEqualTo(960);
    assertThat(config23SampleCount).isEqualTo(1920);
    assertThat(config24SampleCount).isEqualTo(240);
    assertThat(config25SampleCount).isEqualTo(480);
    assertThat(config26SampleCount).isEqualTo(960);
    assertThat(config27SampleCount).isEqualTo(1920);
    assertThat(config28SampleCount).isEqualTo(240);
    assertThat(config29SampleCount).isEqualTo(480);
    assertThat(config30SampleCount).isEqualTo(960);
    assertThat(config31SampleCount).isEqualTo(1920);
  }

  @Test
  public void getPacketAudioSampleCount_code2_returnsExpectedDuration() {
    int config0SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("06"));
    int config1SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("0E"));
    int config2SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("16"));
    int config3SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("1E"));
    int config4SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("26"));
    int config5SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("2E"));
    int config6SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("36"));
    int config7SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("3E"));
    int config8SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("46"));
    int config9SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("4E"));
    int config10SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("56"));
    int config11SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("5E"));
    int config12SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("66"));
    int config13SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("6E"));
    int config14SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("76"));
    int config15SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("7E"));
    int config16SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("86"));
    int config17SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("8E"));
    int config18SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("96"));
    int config19SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("9E"));
    int config20SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("A6"));
    int config21SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("AE"));
    int config22SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("B6"));
    int config23SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("BE"));
    int config24SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("C6"));
    int config25SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("CE"));
    int config26SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("D6"));
    int config27SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("DE"));
    int config28SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("E6"));
    int config29SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("EE"));
    int config30SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("F6"));
    int config31SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("FE"));

    assertThat(config0SampleCount).isEqualTo(960);
    assertThat(config1SampleCount).isEqualTo(1920);
    assertThat(config2SampleCount).isEqualTo(3840);
    assertThat(config3SampleCount).isEqualTo(5760);
    assertThat(config4SampleCount).isEqualTo(960);
    assertThat(config5SampleCount).isEqualTo(1920);
    assertThat(config6SampleCount).isEqualTo(3840);
    assertThat(config7SampleCount).isEqualTo(5760);
    assertThat(config8SampleCount).isEqualTo(960);
    assertThat(config9SampleCount).isEqualTo(1920);
    assertThat(config10SampleCount).isEqualTo(3840);
    assertThat(config11SampleCount).isEqualTo(5760);
    assertThat(config12SampleCount).isEqualTo(960);
    assertThat(config13SampleCount).isEqualTo(1920);
    assertThat(config14SampleCount).isEqualTo(960);
    assertThat(config15SampleCount).isEqualTo(1920);
    assertThat(config16SampleCount).isEqualTo(240);
    assertThat(config17SampleCount).isEqualTo(480);
    assertThat(config18SampleCount).isEqualTo(960);
    assertThat(config19SampleCount).isEqualTo(1920);
    assertThat(config20SampleCount).isEqualTo(240);
    assertThat(config21SampleCount).isEqualTo(480);
    assertThat(config22SampleCount).isEqualTo(960);
    assertThat(config23SampleCount).isEqualTo(1920);
    assertThat(config24SampleCount).isEqualTo(240);
    assertThat(config25SampleCount).isEqualTo(480);
    assertThat(config26SampleCount).isEqualTo(960);
    assertThat(config27SampleCount).isEqualTo(1920);
    assertThat(config28SampleCount).isEqualTo(240);
    assertThat(config29SampleCount).isEqualTo(480);
    assertThat(config30SampleCount).isEqualTo(960);
    assertThat(config31SampleCount).isEqualTo(1920);
  }

  @Test
  public void getPacketAudioSampleCount_code3_returnsExpectedDuration() {
    // max possible frame count to reach 120ms duration
    int config0SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("078C"));
    int config1SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("0F86"));
    int config2SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("1783"));
    int config3SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("1F82"));
    // frame count of 2
    int config4SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("2782"));
    int config5SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("2F82"));
    int config6SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("3782"));
    int config7SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("3F82"));
    int config8SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("4782"));
    int config9SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("4F82"));
    int config10SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("5782"));
    int config11SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("5F82"));
    // max possible frame count to reach 120ms duration
    int config12SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("678C"));
    int config13SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("6F86"));
    // frame count of 2
    int config14SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("7782"));
    int config15SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("7F82"));
    // max possible frame count to reach 120ms duration
    int config16SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("87B0"));
    int config17SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("8F98"));
    int config18SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("978C"));
    int config19SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("9F86"));
    // frame count of 2
    int config20SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("A782"));
    int config21SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("AF82"));
    int config22SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("B782"));
    int config23SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("BF82"));
    int config24SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("C782"));
    int config25SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("CF82"));
    int config26SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("D782"));
    int config27SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("DF82"));
    int config28SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("E782"));
    int config29SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("EF82"));
    int config30SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("F782"));
    int config31SampleCount = OpusUtil.parsePacketAudioSampleCount(getByteBuffer("FF82"));

    assertThat(config0SampleCount).isEqualTo(5760);
    assertThat(config1SampleCount).isEqualTo(5760);
    assertThat(config2SampleCount).isEqualTo(5760);
    assertThat(config3SampleCount).isEqualTo(5760);
    assertThat(config4SampleCount).isEqualTo(960);
    assertThat(config5SampleCount).isEqualTo(1920);
    assertThat(config6SampleCount).isEqualTo(3840);
    assertThat(config7SampleCount).isEqualTo(5760);
    assertThat(config8SampleCount).isEqualTo(960);
    assertThat(config9SampleCount).isEqualTo(1920);
    assertThat(config10SampleCount).isEqualTo(3840);
    assertThat(config11SampleCount).isEqualTo(5760);
    assertThat(config12SampleCount).isEqualTo(5760);
    assertThat(config13SampleCount).isEqualTo(5760);
    assertThat(config14SampleCount).isEqualTo(960);
    assertThat(config15SampleCount).isEqualTo(1920);
    assertThat(config16SampleCount).isEqualTo(5760);
    assertThat(config17SampleCount).isEqualTo(5760);
    assertThat(config18SampleCount).isEqualTo(5760);
    assertThat(config19SampleCount).isEqualTo(5760);
    assertThat(config20SampleCount).isEqualTo(240);
    assertThat(config21SampleCount).isEqualTo(480);
    assertThat(config22SampleCount).isEqualTo(960);
    assertThat(config23SampleCount).isEqualTo(1920);
    assertThat(config24SampleCount).isEqualTo(240);
    assertThat(config25SampleCount).isEqualTo(480);
    assertThat(config26SampleCount).isEqualTo(960);
    assertThat(config27SampleCount).isEqualTo(1920);
    assertThat(config28SampleCount).isEqualTo(240);
    assertThat(config29SampleCount).isEqualTo(480);
    assertThat(config30SampleCount).isEqualTo(960);
    assertThat(config31SampleCount).isEqualTo(1920);
  }

  private static long sampleCountToNanoseconds(long sampleCount) {
    return (sampleCount * C.NANOS_PER_SECOND) / OpusUtil.SAMPLE_RATE;
  }

  private static byte[] buildNativeOrderByteArray(long value) {
    return ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(value).array();
  }

  private static ByteBuffer getByteBuffer(String hexString) {
    return ByteBuffer.wrap(getBytesFromHexString(hexString));
  }
}
