/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.MimeTypes.AUDIO_AAC;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_H264;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link Mp4MetadataInfo}. */
@RunWith(AndroidJUnit4.class)
public class Mp4MetadataInfoTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void create_withEmptyFile_throws() throws IOException {
    String emptyFilePath = temporaryFolder.newFile("EmptyFile").getPath();

    assertThrows(IllegalStateException.class, () -> Mp4MetadataInfo.create(context, emptyFilePath));
  }

  @Test
  public void create_withNonMp4File_throws() {
    String nonMp4FilePath = "asset:///media/mkv/sample.mkv";

    assertThrows(
        IllegalStateException.class, () -> Mp4MetadataInfo.create(context, nonMp4FilePath));
  }

  @Test
  public void lastSyncSampleTimestampUs_ofSmallMp4File_outputsFirstTimestamp() throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);

    assertThat(mp4MetadataInfo.lastSyncSampleTimestampUs)
        .isEqualTo(0); // The timestamp of the first sample in sample.mp4.
  }

  @Test
  public void lastSyncSampleTimestampUs_ofMp4File_outputMatchesExpected() throws IOException {
    String mp4FilePath = "asset:///media/mp4/hdr10-720p.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);

    assertThat(mp4MetadataInfo.lastSyncSampleTimestampUs)
        .isEqualTo(4_003_277L); // The timestamp of the last sync sample in hdr10-720p.mp4.
  }

  @Test
  public void lastSyncSampleTimestampUs_ofAudioOnlyMp4File_isUnset() throws IOException {
    String audioOnlyMp4FilePath = "asset:///media/mp4/sample_ac3.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, audioOnlyMp4FilePath);

    assertThat(mp4MetadataInfo.lastSyncSampleTimestampUs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void durationUs_ofMp4File_outputMatchesExpected() throws Exception {
    String mp4FilePath = "asset:///media/mp4/hdr10-720p.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);

    assertThat(mp4MetadataInfo.durationUs).isEqualTo(4_236_600L); // The duration of hdr10-720p.mp4.
  }

  @Test
  public void firstSyncSampleTimestampUsAfterTimeUs_timeUsIsSyncSample_outputsFirstTimestamp()
      throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath, /* timeUs= */ 0);

    assertThat(mp4MetadataInfo.firstSyncSampleTimestampUsAfterTimeUs)
        .isEqualTo(0); // The timestamp of the first sample in sample.mp4.
  }

  @Test
  public void firstSyncSampleTimestampUsAfterTimeUs_timeUsNotASyncSample_returnsCorrectTimestamp()
      throws IOException {
    String mp4FilePath = "asset:///media/mp4/hdr10-720p.mp4";
    Mp4MetadataInfo mp4MetadataInfo =
        Mp4MetadataInfo.create(context, mp4FilePath, /* timeUs= */ 400);

    assertThat(mp4MetadataInfo.firstSyncSampleTimestampUsAfterTimeUs).isEqualTo(1_002_955L);
  }

  @Test
  public void firstSyncSampleTimestampUsAfterTimeUs_timeUsSetToDuration_returnsTimeEndOfSource()
      throws IOException {
    String mp4FilePath = "asset:///media/mp4/hdr10-720p.mp4";
    Mp4MetadataInfo mp4MetadataInfo =
        Mp4MetadataInfo.create(context, mp4FilePath, /* timeUs= */ 4_236_600L);

    assertThat(mp4MetadataInfo.firstSyncSampleTimestampUsAfterTimeUs)
        .isEqualTo(C.TIME_END_OF_SOURCE);
  }

  @Test
  public void firstSyncSampleTimestampUsAfterTimeUs_ofAudioOnlyMp4File_returnsUnsetValue()
      throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample_ac3.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath, /* timeUs= */ 0);

    assertThat(mp4MetadataInfo.firstSyncSampleTimestampUsAfterTimeUs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void videoFormat_outputsFormatObjectWithCorrectRelevantFormatData() throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);
    byte[] expectedCsd0 = {
      0, 0, 0, 1, 103, 100, 0, 31, -84, -39, 64, 68, 5, -66, 95, 1, 16, 0, 0, 62, -112, 0, 14, -90,
      0, -15, -125, 25, 96
    };
    byte[] expectedCsd1 = {0, 0, 0, 1, 104, -21, -29, -53, 34, -64};

    Format actualFormat = mp4MetadataInfo.videoFormat;

    assertThat(actualFormat).isNotNull();
    assertThat(actualFormat.initializationData.get(0)).isEqualTo(expectedCsd0);
    assertThat(actualFormat.initializationData.get(1)).isEqualTo(expectedCsd1);
    assertThat(actualFormat.sampleMimeType).isEqualTo(VIDEO_H264);
    assertThat(actualFormat.width).isEqualTo(1080);
    assertThat(actualFormat.height).isEqualTo(720);
    assertThat(actualFormat.rotationDegrees).isEqualTo(0);
    assertThat(actualFormat.pixelWidthHeightRatio).isEqualTo(1);
  }

  @Test
  public void videoFormat_audioOnlyMp4File_outputsNull() throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample_ac3.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);

    assertThat(mp4MetadataInfo.videoFormat).isNull();
  }

  @Test
  public void audioFormat_outputsFormatObjectWithCorrectRelevantFormatData() throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);
    byte[] expectedCsd0 = {18, 8};

    Format actualFormat = mp4MetadataInfo.audioFormat;

    assertThat(actualFormat).isNotNull();
    assertThat(actualFormat.sampleMimeType).isEqualTo(AUDIO_AAC);
    assertThat(actualFormat.channelCount).isEqualTo(1);
    assertThat(actualFormat.sampleRate).isEqualTo(44100);
    assertThat(actualFormat.initializationData.get(0)).isEqualTo(expectedCsd0);
  }

  @Test
  public void audioFormat_videoOnlyMp4File_outputsNull() throws IOException {
    String mp4FilePath = "asset:///media/mp4/sample_18byte_nclx_colr.mp4";
    Mp4MetadataInfo mp4MetadataInfo = Mp4MetadataInfo.create(context, mp4FilePath);

    assertThat(mp4MetadataInfo.audioFormat).isNull();
  }
}
