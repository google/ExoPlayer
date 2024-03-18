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
package androidx.media3.transformer.mh;

import static androidx.media3.test.utils.TestUtil.retrieveTrackFormat;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR_FORMAT;
import static androidx.media3.transformer.mh.HdrCapabilitiesUtil.assumeDeviceSupportsOpenGlToneMapping;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * Composition#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL HDR to SDR tone mapping edit}.
 */
@RunWith(AndroidJUnit4.class)
public class ToneMapHdrToSdrUsingOpenGlTest {
  @Rule public final TestName testName = new TestName();
  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_toneMap_hlg10File_toneMaps() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT);

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_1080P_5_SECOND_HLG10);
  }

  @Test
  public void export_toneMap_hdr10File_toneMaps() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT);

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_720P_4_SECOND_HDR10);
  }

  @Test
  public void export_toneMap_dolbyVisionFile_toneMaps() throws Exception {
    assumeDeviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_DOLBY_VISION_HDR_FORMAT);

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_DOLBY_VISION_HDR);
  }

  private void runTransformerWithOpenGlToneMapping(String testId, String fileUri) throws Exception {
    Transformer transformer = new Transformer.Builder(context).build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence(
                    new EditedMediaItem.Builder(MediaItem.fromUri(fileUri)).build()))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build();
    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, composition);
    @C.ColorTransfer
    int actualColorTransfer =
        retrieveTrackFormat(context, exportTestResult.filePath, C.TRACK_TYPE_VIDEO)
            .colorInfo
            .colorTransfer;
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
  }
}
