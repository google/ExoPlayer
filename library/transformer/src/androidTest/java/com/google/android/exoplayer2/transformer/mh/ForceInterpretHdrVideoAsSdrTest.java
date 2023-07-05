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

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_720P_4_SECOND_HDR10_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.recordTestSkipped;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.skipAndLogIfFormatsUnsupported;
import static com.google.android.exoplayer2.transformer.mh.FileUtil.assertFileHasColorTransfer;
import static com.google.android.exoplayer2.util.Util.SDK_INT;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.transformer.ExportTestResult;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.transformer.TransformerAndroidTestRunner;
import com.google.android.exoplayer2.video.ColorInfo;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for {@linkplain
 * TransformationRequest#HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR forcing HDR contents to be
 * interpreted as SDR}.
 */
@RunWith(AndroidJUnit4.class)
public class ForceInterpretHdrVideoAsSdrTest {

  @Test
  public void forceInterpretHdrVideoAsSdrTest_hdr10File_transformsOrThrows() throws Exception {
    String testId = "forceInterpretHdrVideoAsSdrTest_hdr10File_transformsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    if (SDK_INT < 29) {
      // TODO(b/269759013): Fix failures under API 29 to expand confidence on all API versions.
      recordTestSkipped(
          context, testId, /* reason= */ "Under API 29, this API is considered best-effort.");
      return;
    }

    // Force interpret HDR as SDR signals SDR input to the decoder, even if the actual input is HDR.
    Format decoderInputFormat =
        MP4_ASSET_720P_4_SECOND_HDR10_FORMAT
            .buildUpon()
            .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
            .build();
    if (skipAndLogIfFormatsUnsupported(
        context, testId, decoderInputFormat, /* outputFormat= */ null)) {
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(
                        TransformationRequest.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR)
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_720P_4_SECOND_HDR10));
    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertFileHasColorTransfer(context, exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
  }

  @Test
  public void forceInterpretHdrVideoAsSdrTest_hlg10File_transformsOrThrows() throws Exception {
    String testId = "forceInterpretHdrVideoAsSdrTest_hlg10File_transformsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    if (SDK_INT < 29) {
      // TODO(b/269759013): Fix failures under API 29 to expand confidence on all API versions.
      recordTestSkipped(
          context, testId, /* reason= */ "Under API 29, this API is considered best-effort.");
      return;
    }

    // Force interpret HDR as SDR signals SDR input to the decoder, even if the actual input is HDR.
    Format decoderInputFormat =
        MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT
            .buildUpon()
            .setColorInfo(ColorInfo.SDR_BT709_LIMITED)
            .build();
    if (skipAndLogIfFormatsUnsupported(
        context, testId, decoderInputFormat, /* outputFormat= */ null)) {
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(
                        TransformationRequest.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR)
                    .build())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10));
    ExportTestResult exportTestResult =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, mediaItem);

    assertFileHasColorTransfer(context, exportTestResult.filePath, C.COLOR_TRANSFER_SDR);
  }
}
