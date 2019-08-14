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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;
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
  public void getCodecProfileAndLevel_handlesAv1ProfileMain8CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        "av01.0.10M.08",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8,
        MediaCodecInfo.CodecProfileLevel.AV1Level42);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain10CodecString() {
    assertCodecProfileAndLevelForCodecsString(
        "av01.0.20M.10",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
        MediaCodecInfo.CodecProfileLevel.AV1Level7);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain10HDRWithHdrInfoSet() {
    ColorInfo colorInfo =
        new ColorInfo(
            /* colorSpace= */ C.COLOR_SPACE_BT709,
            /* colorRange= */ C.COLOR_RANGE_LIMITED,
            /* colorTransfer= */ C.COLOR_TRANSFER_SDR,
            /* hdrStaticInfo= */ new byte[] {1, 2, 3, 4, 5, 6, 7});
    Format format =
        Format.createVideoSampleFormat(
            /* id= */ null,
            /* sampleMimeType= */ MimeTypes.VIDEO_UNKNOWN,
            /* codecs= */ "av01.0.21M.10",
            /* bitrate= */ Format.NO_VALUE,
            /* maxInputSize= */ Format.NO_VALUE,
            /* width= */ 1024,
            /* height= */ 768,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* rotationDegrees= */ Format.NO_VALUE,
            /* pixelWidthHeightRatio= */ 0,
            /* projectionData= */ null,
            /* stereoMode= */ Format.NO_VALUE,
            /* colorInfo= */ colorInfo,
            /* drmInitData */ null);
    assertCodecProfileAndLevelForFormat(
        format,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.AV1Level71);
  }

  @Test
  public void getCodecProfileAndLevel_handlesAv1ProfileMain10HDRWithoutHdrInfoSet() {
    ColorInfo colorInfo =
        new ColorInfo(
            /* colorSpace= */ C.COLOR_SPACE_BT709,
            /* colorRange= */ C.COLOR_RANGE_LIMITED,
            /* colorTransfer= */ C.COLOR_TRANSFER_HLG,
            /* hdrStaticInfo= */ null);
    Format format =
        Format.createVideoSampleFormat(
            /* id= */ null,
            /* sampleMimeType= */ MimeTypes.VIDEO_UNKNOWN,
            /* codecs= */ "av01.0.21M.10",
            /* bitrate= */ Format.NO_VALUE,
            /* maxInputSize= */ Format.NO_VALUE,
            /* width= */ 1024,
            /* height= */ 768,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* rotationDegrees= */ Format.NO_VALUE,
            /* pixelWidthHeightRatio= */ 0,
            /* projectionData= */ null,
            /* stereoMode= */ Format.NO_VALUE,
            /* colorInfo= */ colorInfo,
            /* drmInitData */ null);
    assertCodecProfileAndLevelForFormat(
        format,
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
        MediaCodecInfo.CodecProfileLevel.AV1Level71);
  }

  @Test
  public void getCodecProfileAndLevel_handlesFullAv1CodecString() {
    // Example from https://aomediacodec.github.io/av1-isobmff/#codecsparam.
    assertCodecProfileAndLevelForCodecsString(
        "av01.0.04M.10.0.112.09.16.09.0",
        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
        MediaCodecInfo.CodecProfileLevel.AV1Level3);
  }

  @Test
  public void getCodecProfileAndLevel_rejectsNullCodecString() {
    Format format =
        Format.createVideoSampleFormat(
            /* id= */ null,
            /* sampleMimeType= */ MimeTypes.VIDEO_UNKNOWN,
            /* codecs= */ null,
            /* bitrate= */ Format.NO_VALUE,
            /* maxInputSize= */ Format.NO_VALUE,
            /* width= */ 1024,
            /* height= */ 768,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* drmInitData= */ null);
    assertThat(MediaCodecUtil.getCodecProfileAndLevel(format)).isNull();
  }

  @Test
  public void getCodecProfileAndLevel_rejectsEmptyCodecString() {
    Format format =
        Format.createVideoSampleFormat(
            /* id= */ null,
            /* sampleMimeType= */ MimeTypes.VIDEO_UNKNOWN,
            /* codecs= */ "",
            /* bitrate= */ Format.NO_VALUE,
            /* maxInputSize= */ Format.NO_VALUE,
            /* width= */ 1024,
            /* height= */ 768,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* drmInitData= */ null);
    assertThat(MediaCodecUtil.getCodecProfileAndLevel(format)).isNull();
  }

  private static void assertCodecProfileAndLevelForCodecsString(
      String codecs, int profile, int level) {
    Format format =
        Format.createVideoSampleFormat(
            /* id= */ null,
            /* sampleMimeType= */ MimeTypes.VIDEO_UNKNOWN,
            /* codecs= */ codecs,
            /* bitrate= */ Format.NO_VALUE,
            /* maxInputSize= */ Format.NO_VALUE,
            /* width= */ 1024,
            /* height= */ 768,
            /* frameRate= */ Format.NO_VALUE,
            /* initializationData= */ null,
            /* drmInitData= */ null);
    assertCodecProfileAndLevelForFormat(format, profile, level);
  }

  private static void assertCodecProfileAndLevelForFormat(Format format, int profile, int level) {
    Pair<Integer, Integer> codecProfileAndLevel = MediaCodecUtil.getCodecProfileAndLevel(format);
    assertThat(codecProfileAndLevel).isNotNull();
    assertThat(codecProfileAndLevel.first).isEqualTo(profile);
    assertThat(codecProfileAndLevel.second).isEqualTo(level);
  }
}
