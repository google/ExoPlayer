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
package androidx.media3.common;

import static androidx.media3.common.C.WIDEVINE_UUID;
import static androidx.media3.common.MimeTypes.VIDEO_MP4;
import static androidx.media3.common.MimeTypes.VIDEO_WEBM;
import static androidx.media3.test.utils.TestUtil.buildTestData;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.os.Bundle;
import androidx.media3.test.utils.FakeMetadataEntry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
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
  public void roundTripViaBundle_ofParameters_yieldsEqualInstance() {
    Format formatToBundle = createTestFormat();
    Format formatFromBundle = Format.fromBundle(formatToBundle.toBundle());

    assertThat(formatFromBundle).isEqualTo(formatToBundle);
  }

  @Test
  public void roundTripViaBundle_excludeMetadata_hasMetadataExcluded() {
    Format format = createTestFormat();

    Bundle bundleWithMetadataExcluded = format.toBundle(/* excludeMetadata= */ true);

    Format formatWithMetadataExcluded = Format.fromBundle(bundleWithMetadataExcluded);
    assertThat(formatWithMetadataExcluded).isEqualTo(format.buildUpon().setMetadata(null).build());
  }

  @Test
  public void formatBuild_withLabelAndWithoutLabels_labelIsInLabels() {
    Format format = new Format.Builder().setLabel("label").setLabels(ImmutableList.of()).build();

    assertThat(format.label).isEqualTo("label");
    assertThat(format.labels).hasSize(1);
    assertThat(format.labels.get(0).value).isEqualTo("label");
  }

  @Test
  public void formatBuild_withLabelsAndLanguageMatchingAndWithoutLabel_theLanguageMatchIsInLabel() {
    Format format =
        new Format.Builder()
            .setLabel(null)
            .setLabels(
                ImmutableList.of(
                    new Label("en", "nonDefaultLabel"), new Label("zh", "matchingLabel")))
            .setLanguage("zh")
            .build();

    assertThat(format.label).isEqualTo("matchingLabel");
  }

  @Test
  public void formatBuild_withLabelsAndNoLanguageMatchingAndWithoutLabel_theFirstIsInLabel() {
    Format format =
        new Format.Builder()
            .setLabel(null)
            .setLabels(
                ImmutableList.of(new Label("fr", "firstLabel"), new Label("de", "secondLabel")))
            .setLanguage("en")
            .build();

    assertThat(format.label).isEqualTo("firstLabel");
  }

  @Test
  public void formatBuild_withoutLabelsOrLabel_bothEmpty() {
    Format format = createTestFormat();
    format = format.buildUpon().setLabel(null).setLabels(ImmutableList.of()).build();

    assertThat(format.label).isNull();
    assertThat(format.labels).isEmpty();
  }

  @Test
  public void formatBuild_withLabelAndLabelsSetButNoMatch_throwsException() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new Format.Builder()
                .setLabel("otherLabel")
                .setLabels(ImmutableList.of(new Label("en", "label")))
                .build());
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
    Metadata metadata = new Metadata(new FakeMetadataEntry("id1"), new FakeMetadataEntry("id2"));

    ColorInfo colorInfo =
        new ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT709)
            .setColorRange(C.COLOR_RANGE_LIMITED)
            .setColorTransfer(C.COLOR_TRANSFER_SDR)
            .setHdrStaticInfo(new byte[] {1, 2, 3, 4, 5, 6, 7})
            .setLumaBitdepth(9)
            .setChromaBitdepth(11)
            .build();

    return new Format.Builder()
        .setId("id")
        .setLabel("label")
        .setLabels(ImmutableList.of(new Label("en", "label")))
        .setLanguage("language")
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .setRoleFlags(C.ROLE_FLAG_MAIN)
        .setAverageBitrate(1024)
        .setPeakBitrate(2048)
        .setCodecs("codec")
        .setMetadata(metadata)
        .setContainerMimeType(VIDEO_MP4)
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
        .setCryptoType(C.CRYPTO_TYPE_CUSTOM_BASE)
        .setTileCountHorizontal(20)
        .setTileCountVertical(40)
        .build();
  }
}
