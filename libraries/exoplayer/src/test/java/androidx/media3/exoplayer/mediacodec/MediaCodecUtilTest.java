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
package androidx.media3.exoplayer.mediacodec;

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaCodecInfo;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaCodecUtil}. */
@RunWith(AndroidJUnit4.class)
public final class MediaCodecUtilTest {

  @Test
  public void getCodecProfileAndLevel_handlesVp9Profile1CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_VP9,
        "vp09.01.51",
        MediaCodecInfo.CodecProfileLevel.VP9Profile1,
        MediaCodecInfo.CodecProfileLevel.VP9Level51);
  }

  @Test
  public void getCodecProfileAndLevel_handlesVp9Profile2CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_VP9,
        "vp09.02.10",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Level1);
  }

  @Test
  public void getCodecProfileAndLevel_handlesFullVp9CodecString() {
    // Example from https://www.webmproject.org/vp9/mp4/#codecs-parameter-string.
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_VP9,
        "vp09.02.10.10.01.09.16.09.01",
        MediaCodecInfo.CodecProfileLevel.VP9Profile2,
        MediaCodecInfo.CodecProfileLevel.VP9Level1);
  }

  @Test
  public void getCodecProfileAndLevel_handlesDolbyVisionCodecString() {
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_DOLBY_VISION,
        "dvh1.05.05",
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn,
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelFhd60);
  }

  @Test
  public void getCodecProfileAndLevel_handlesDolbyVisionProfile10CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_DOLBY_VISION,
        "dav1.10.09",
        MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvav110,
        MediaCodecInfo.CodecProfileLevel.DolbyVisionLevelUhd60);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain8CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_AV1,
        "av01.0.10M.08",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8,
        MediaCodecInfo.CodecProfileLevel.AV1Level42);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain10CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_AV1,
        "av01.0.20M.10",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
        MediaCodecInfo.CodecProfileLevel.AV1Level7);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain10HDRWithHdrInfoSet() {
    ColorInfo colorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_SDR)
            .setHdrStaticInfo(new byte[] {1, 2, 3, 4, 5, 6, 7})
            .build();
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setCodecs("av01.0.21M.10")
            .setColorInfo(colorInfo)
            .build();
    assertCodecProfileAndLevelForFormat(
        format,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.AV1Level71);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain10HDRWithoutHdrInfoSet() {
    ColorInfo colorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_HLG)
            .build();
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_AV1)
            .setCodecs("av01.0.21M.10")
            .setColorInfo(colorInfo)
            .build();
    assertCodecProfileAndLevelForFormat(
        format,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.AV1Level71);
  }

  @Test
  public void getCodecProfileAndLevel_handlesFullAv1CodecString() {
    // Example from https://aomediacodec.github.io/av1-isobmff/#codecsparam.
    assertCodecProfileAndLevelForCodecsString(
        MimeTypes.VIDEO_AV1,
        "av01.0.04M.10.0.112.09.16.09.0",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
        MediaCodecInfo.CodecProfileLevel.AV1Level3);
  }

  @Test
  public void getCodecProfileAndLevel_rejectsNullCodecString() {
    Format format = new Format.Builder().setCodecs(null).build();
    assertThat(MediaCodecUtil.getCodecProfileAndLevel(format)).isNull();
  }

  @Test
  public void getCodecProfileAndLevel_rejectsEmptyCodecString() {
    Format format = new Format.Builder().setCodecs("").build();
    assertThat(MediaCodecUtil.getCodecProfileAndLevel(format)).isNull();
  }

  private static void assertCodecProfileAndLevelForCodecsString(
      String sampleMimeType, String codecs, int profile, int level) {
    Format format =
        new Format.Builder().setSampleMimeType(sampleMimeType).setCodecs(codecs).build();
    assertCodecProfileAndLevelForFormat(format, profile, level);
  }

  private static void assertCodecProfileAndLevelForFormat(Format format, int profile, int level) {
    @Nullable
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
    assertThat(codecProfileAndLevel).isNotNull();
    assertThat(codecProfileAndLevel.first).isEqualTo(profile);
    assertThat(codecProfileAndLevel.second).isEqualTo(level);
  }
}
