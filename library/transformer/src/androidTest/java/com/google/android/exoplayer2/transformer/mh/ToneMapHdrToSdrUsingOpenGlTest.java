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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_DOLBY_VISION_HDR_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.recordTestSkipped;
import static com.google.android.exoplayer2.transformer.mh.FileUtil.assertFileHasColorTransfer;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.transformer.AndroidTestUtil;
import com.google.android.exoplayer2.transformer.ExportTestResult;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.io.IOException;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * TransformationRequest#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL HDR to SDR tone mapping edit}.
 */
@RunWith(AndroidJUnit4.class)
public class ToneMapHdrToSdrUsingOpenGlTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void export_toneMap_hlg10File_toneMaps() throws Exception {
    String testId = "export_glToneMap_hlg10File_toneMaps";
    if (!deviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT)) {
      return;
    }

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_1080P_5_SECOND_HLG10);
  }

  @Test
  public void export_toneMap_hdr10File_toneMaps() throws Exception {
    String testId = "export_glToneMap_hdr10File_toneMaps";
    if (!deviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_720P_4_SECOND_HDR10_FORMAT)) {
      return;
    }

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_720P_4_SECOND_HDR10);
  }

  @Test
  public void export_toneMap_dolbyVisionFile_toneMaps() throws Exception {
    String testId = "export_toneMap_dolbyVisionFile_toneMaps";
    if (!deviceSupportsOpenGlToneMapping(
        testId, /* inputFormat= */ MP4_ASSET_DOLBY_VISION_HDR_FORMAT)) {
      return;
    }

    runTransformerWithOpenGlToneMapping(testId, MP4_ASSET_DOLBY_VISION_HDR);
  }

  private void runTransformerWithOpenGlToneMapping(String testId, String fileUri) throws Exception {
    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build())
            .build();
    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, MediaItem.fromUri(fileUri));
    assertFileHasColorTransfer(context, exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
  }

  private static boolean deviceSupportsOpenGlToneMapping(String testId, Format inputFormat)
      throws JSONException, IOException, MediaCodecUtil.DecoderQueryException {
    Context context = getApplicationContext();
    if (Util.SDK_INT < 29) {
      recordTestSkipped(
          context,
          testId,
          /* reason= */ "OpenGL-based HDR to SDR tone mapping is only supported on API 29+.");
      return false;
    }

    if (!GlUtil.isYuvTargetExtensionSupported()) {
      recordTestSkipped(context, testId, /* reason= */ "Device lacks YUV extension support.");
      return false;
    }

    return !AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        inputFormat,
        /* outputFormat= */ inputFormat
            .buildUpon()
            .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
            .build());
  }
}
