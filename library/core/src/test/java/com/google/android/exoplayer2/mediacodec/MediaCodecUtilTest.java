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
package com.google.android.exoplayer2.mediacodec;

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaCodecInfo;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaCodecUtil}. */
@RunWith(AndroidJUnit4.class)
public final class MediaCodecUtilTest {

  @Test
  public void getCodecProfileAndLevel_handlesVp9Profile1CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        "vp09.01.51",
        MediaCodecInfo.CodecProfileLevel.VP9Profile1,
        MediaCodecInfo.CodecProfileLevel.VP9Level51);
  }

  @Test
  public void getCodecProfileAndLevel_handlesVp9Profile2CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        "vp09.02.10",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Level1);
  }

  @Test
  public void getCodecProfileAndLevel_handlesFullVp9CodecString() {
    // Example from https://www.webmproject.org/vp9/mp4/#codecs-parameter-string.
    assertCodecProfileAndLevelForCodecsString(
        "vp09.02.10.10.01.09.16.09.01",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Level1);
  }

  @Test
  public void getCodecProfileAndLevel_handlesDolbyVisionCodecString() {
    assertCodecProfileAndLevelForCodecsString(
        "dvh1.05.05",
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn,
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60);
  }

  @Test
  public void getCodecProfileAndLevel_rejectsNullCodecString() {
    assertThat(MediaCodecUtil.getCodecProfileAndLevel(/* codec= */ null)).isNull();
  }

  @Test
  public void getCodecProfileAndLevel_rejectsEmptyCodecString() {
    assertThat(MediaCodecUtil.getCodecProfileAndLevel("")).isNull();
  }

  private static void assertCodecProfileAndLevelForCodecsString(
      String codecs, int profile, int level) {
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(codecs);
    assertThat(codecProfileAndLevel).isNotNull();
    assertThat(codecProfileAndLevel.first).isEqualTo(profile);
    assertThat(codecProfileAndLevel.second).isEqualTo(level);
  }
}
