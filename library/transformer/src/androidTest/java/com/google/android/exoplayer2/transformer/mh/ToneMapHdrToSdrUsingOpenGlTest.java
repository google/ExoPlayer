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
package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.testutil.FileUtil.retrieveColorTransfer;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR_FORMAT;
import static com.google.android.exoplayer2.transformer.mh.HdrCapabilitiesUtil.skipAndLogIfOpenGlToneMappingUnsupported;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.transformer.Composition;
import com.google.android.exoplayer2.transformer.EditedMediaItem;
import com.google.android.exoplayer2.transformer.EditedMediaItemSequence;
import com.google.android.exoplayer2.transformer.ExportTestResult;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * Composition#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL HDR to SDR tone mapping edit}.
 */
@RunWith(AndroidJUnit4.class)
public class ToneMapHdrToSdrUsingOpenGlTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void export_toneMap_hlg10File_toneMaps() throws Exception {
    String testId = "export_glToneMap_hlg10File_toneMaps";
    if (skipAndLogIfOpenGlToneMappingUnsupported(
        testId, /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT)) {
      return;
    }

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_1080P_5_SECOND_HLG10);
  }

  @Test
  public void export_toneMap_hdr10File_toneMaps() throws Exception {
    String testId = "export_glToneMap_hdr10File_toneMaps";
    if (skipAndLogIfOpenGlToneMappingUnsupported(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT)) {
      return;
    }

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_720P_4_SECOND_HDR10);
  }

  @Test
  public void export_toneMap_dolbyVisionFile_toneMaps() throws Exception {
    String testId = "export_toneMap_dolbyVisionFile_toneMaps";
    if (skipAndLogIfOpenGlToneMappingUnsupported(
        testId, /* inputFormat= */ MP4_ASSET_DOLBY_VISION_HDR_FORMAT)) {
      return;
    }

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
    int actualColorTransfer = retrieveColorTransfer(context, exportTestResult.filePath);
    assertThat(actualColorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
  }
}
