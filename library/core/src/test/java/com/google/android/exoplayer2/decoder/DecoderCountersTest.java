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

package com.google.android.exoplayer2.decoder;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DecoderCounters}. */
@RunWith(AndroidJUnit4.class)
public class DecoderCountersTest {
  private DecoderCounters decoderCounters;

  @Before
  public void setUp() {
    decoderCounters = new DecoderCounters();
  }

  @Test
  public void maybeAddVideoFrameProcessingOffsetSample_addsSamples() {
    long sampleSum = 0;
    for (int i = 0; i < 100; i++) {
      long sample = (i + 10) * 10L;
      sampleSum += sample;
      decoderCounters.addVideoFrameProcessingOffsetSample(sample);
    }

    assertThat(decoderCounters.videoFrameProcessingOffsetUsSum).isEqualTo(sampleSum);
    assertThat(decoderCounters.videoFrameProcessingOffsetUsCount).isEqualTo(100);
  }

  @Test
  public void addVideoFrameProcessingOffsetSample_sumReachesMaxLong_addsValues() {
    long highSampleValue = Long.MAX_VALUE - 10;
    long additionalSample = Long.MAX_VALUE - highSampleValue;

    decoderCounters.addVideoFrameProcessingOffsetSample(highSampleValue);
    decoderCounters.addVideoFrameProcessingOffsetSample(additionalSample);

    assertThat(decoderCounters.videoFrameProcessingOffsetUsSum).isEqualTo(Long.MAX_VALUE);
    assertThat(decoderCounters.videoFrameProcessingOffsetUsCount).isEqualTo(2);
  }

  @Test
  public void addVideoFrameProcessingOffsetSample_sumOverflows_isNoOp() {
    long highSampleValue = Long.MAX_VALUE - 10;
    long additionalSample = Long.MAX_VALUE - highSampleValue + 10;

    decoderCounters.addVideoFrameProcessingOffsetSample(highSampleValue);
    decoderCounters.addVideoFrameProcessingOffsetSample(additionalSample);

    assertThat(decoderCounters.videoFrameProcessingOffsetUsSum).isEqualTo(highSampleValue);
    assertThat(decoderCounters.videoFrameProcessingOffsetUsCount).isEqualTo(1);
  }

  @Test
  public void addVideoFrameProcessingOffsetSample_sumReachesMinLong_addsValues() {
    long lowSampleValue = Long.MIN_VALUE + 10;
    long additionalSample = Long.MIN_VALUE - lowSampleValue;

    decoderCounters.addVideoFrameProcessingOffsetSample(lowSampleValue);
    decoderCounters.addVideoFrameProcessingOffsetSample(additionalSample);

    assertThat(decoderCounters.videoFrameProcessingOffsetUsSum).isEqualTo(Long.MIN_VALUE);
    assertThat(decoderCounters.videoFrameProcessingOffsetUsCount).isEqualTo(2);
  }

  @Test
  public void addVideoFrameProcessingOffsetSample_sumUnderflows_isNoOp() {
    long lowSampleValue = Long.MIN_VALUE + 10;
    long additionalSample = Long.MIN_VALUE - lowSampleValue - 10;

    decoderCounters.addVideoFrameProcessingOffsetSample(lowSampleValue);
    decoderCounters.addVideoFrameProcessingOffsetSample(additionalSample);

    assertThat(decoderCounters.videoFrameProcessingOffsetUsSum).isEqualTo(lowSampleValue);
    assertThat(decoderCounters.videoFrameProcessingOffsetUsCount).isEqualTo(1);
  }
}
