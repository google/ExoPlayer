/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AacUtil}. */
@RunWith(AndroidJUnit4.class)
public final class AacUtilTest {
  private static final byte[] AAC_48K_2CH_HEADER = Util.getBytesFromHexString("1190");

  private static final byte[] NOT_ENOUGH_ARBITRARY_SAMPLING_FREQ_BITS_HEADER =
      Util.getBytesFromHexString("1790");

  private static final byte[] ARBITRARY_SAMPLING_FREQ_BITS_HEADER =
      Util.getBytesFromHexString("1780000790");

  @Test
  public void parseAudioSpecificConfig_twoCh48kAac_parsedCorrectly() throws Exception {
    AacUtil.Config aac = AacUtil.parseAudioSpecificConfig(AAC_48K_2CH_HEADER);

    assertThat(aac.channelCount).isEqualTo(2);
    assertThat(aac.sampleRateHz).isEqualTo(48000);
    assertThat(aac.codecs).isEqualTo("mp4a.40.2");
  }

  @Test
  public void parseAudioSpecificConfig_arbitrarySamplingFreqHeader_parsedCorrectly()
      throws Exception {
    AacUtil.Config aac = AacUtil.parseAudioSpecificConfig(ARBITRARY_SAMPLING_FREQ_BITS_HEADER);
    assertThat(aac.channelCount).isEqualTo(2);
    assertThat(aac.sampleRateHz).isEqualTo(15);
    assertThat(aac.codecs).isEqualTo("mp4a.40.2");
  }

  @Test
  public void
      parseAudioSpecificConfig_arbitrarySamplingFreqHeaderNotEnoughBits_throwsParserException() {
    // ISO 14496-3 1.6.2.1 allows for setting of arbitrary sampling frequency, but if the extra
    // frequency bits are missing, make sure the code will throw an exception.
    assertThrows(
        ParserException.class,
        () -> AacUtil.parseAudioSpecificConfig(NOT_ENOUGH_ARBITRARY_SAMPLING_FREQ_BITS_HEADER));
  }
}
