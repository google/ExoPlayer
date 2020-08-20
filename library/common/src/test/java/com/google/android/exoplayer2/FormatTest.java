/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP4;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_WEBM;
import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.UnsupportedMediaCrypto;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Format}. */
@RunWith(AndroidJUnit4.class)
public final class FormatTest {

  @Test
  public void buildUponFormat_createsEqualFormat() {
    Format testFormat = createTestFormat();
    assertThat(testFormat.buildUpon().build()).isEqualTo(testFormat);
  }

  @Test
  public void parcelFormat_createsEqualFormat_exceptExoMediaCryptoType() {
    Format formatToParcel = createTestFormat();

    Parcel parcel = Parcel.obtain();
    formatToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Format formatFromParcel = Format.CREATOR.createFromParcel(parcel);
    Format expectedFormat =
        formatToParcel.buildUpon().setExoMediaCryptoType(UnsupportedMediaCrypto.class).build();

    assertThat(formatFromParcel.exoMediaCryptoType).isEqualTo(UnsupportedMediaCrypto.class);
    assertThat(formatFromParcel).isEqualTo(expectedFormat);

    parcel.recycle();
  }

  private static Format createTestFormat() {
    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initializationData = new ArrayList<>();
    initializationData.add(initData1);
    initializationData.add(initData2);

    DrmInitData.SchemeData drmData1 =
        new DrmInitData.SchemeData(WIDEVINE_UUID, VIDEO_MP4, buildTestData(128, 1 /* data seed */));
    DrmInitData.SchemeData drmData2 =
        new DrmInitData.SchemeData(C.UUID_NIL, VIDEO_WEBM, buildTestData(128, 1 /* data seed */));
    DrmInitData drmInitData = new DrmInitData(drmData1, drmData2);

    byte[] projectionData = new byte[] {1, 2, 3};

    Metadata metadata =
        new Metadata(
            new TextInformationFrame("id1", "description1", "value1"),
            new TextInformationFrame("id2", "description2", "value2"));

    ColorInfo colorInfo =
        new ColorInfo(
            C.COLOR_SPACE_BT709,
            C.COLOR_RANGE_LIMITED,
            C.COLOR_TRANSFER_SDR,
            new byte[] {1, 2, 3, 4, 5, 6, 7});

    return new Format.Builder()
        .setId("id")
        .setLabel("label")
        .setLanguage("language")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .setRoleFlags(C.ROLE_FLAG_MAIN)
        .setAverageBitrate(1024)
        .setPeakBitrate(2048)
        .setCodecs("codec")
        .setMetadata(metadata)
        .setContainerMimeType(MimeTypes.VIDEO_MP4)
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setMaxInputSize(5000)
        .setInitializationData(initializationData)
        .setDrmInitData(drmInitData)
        .setSubsampleOffsetUs(Format.OFFSET_SAMPLE_RELATIVE)
        .setWidth(1920)
        .setHeight(1080)
        .setFrameRate(24)
        .setRotationDegrees(90)
        .setPixelWidthHeightRatio(4)
        .setProjectionData(projectionData)
        .setStereoMode(C.STEREO_MODE_TOP_BOTTOM)
        .setColorInfo(colorInfo)
        .setChannelCount(6)
        .setSampleRate(44100)
        .setPcmEncoding(C.ENCODING_PCM_24BIT)
        .setEncoderDelay(1001)
        .setEncoderPadding(1002)
        .setAccessibilityChannel(2)
        .setExoMediaCryptoType(ExoMediaCrypto.class)
        .build();
  }

  /** Generates an array of random bytes with the specified length. */
  // TODO(internal b/161804035): Use TestUtils when it's available in a dependency we can use here.
  private static byte[] buildTestData(int length, int seed) {
    byte[] source = new byte[length];
    new Random(seed).nextBytes(source);
    return source;
  }
}
