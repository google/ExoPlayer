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
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link CodecSpecificDataUtil}. */
@RunWith(AndroidJUnit4.class)
public class CodecSpecificDataUtilTest {

  @Test
  public void parseAlacAudioSpecificConfig() {
    byte[] alacSpecificConfig =
        new byte[] {
          0, 0, 16, 0, // frameLength
          0, // compatibleVersion
          16, // bitDepth
          40, 10, 14, // tuning parameters
          2, // numChannels = 2
          0, 0, // maxRun
          0, 0, 64, 4, // maxFrameBytes
          0, 46, -32, 0, // avgBitRate
          0, 1, 119, 0, // sampleRate = 96000
        };
    Pair<Integer, Integer> sampleRateAndChannelCount =
        CodecSpecificDataUtil.parseAlacAudioSpecificConfig(alacSpecificConfig);
    assertThat(sampleRateAndChannelCount.first).isEqualTo(96000);
    assertThat(sampleRateAndChannelCount.second).isEqualTo(2);
  }
}
