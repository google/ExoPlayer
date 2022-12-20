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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.recordTestSkipped;
import static androidx.media3.transformer.mh.analysis.FileUtil.assertFileHasColorTransfer;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.AndroidTestUtil;
import androidx.media3.transformer.TransformationException;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.TransformationTestResult;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * {@link Transformer} instrumentation test for applying an {@linkplain
 * TransformationRequest#HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL HDR to SDR tone mapping edit}.
 */
@RunWith(AndroidJUnit4.class)
public class ToneMapHdrToSdrUsingOpenGlTest {
  public static final String TAG = "ToneMapHdrToSdrUsingOpenGlTest";

  @Test
  public void transform_toneMap_hlg10File_toneMapsOrThrows() throws Exception {
    String testId = "transform_glToneMap_hlg10File_toneMapsOrThrows";

    if (Util.SDK_INT < 29) {
      recordTestSkipped(
          ApplicationProvider.getApplicationContext(),
          testId,
          /* reason= */ "OpenGL-based HDR to SDR tone mapping is only supported on API 29+.");
      return;
    }

    if (!GlUtil.isYuvTargetExtensionSupported()) {
      recordTestSkipped(
          getApplicationContext(), testId, /* reason= */ "Device lacks YUV extension support.");
      return;
    }

    if (AndroidTestUtil.skipAndLogIfInsufficientCodecSupport(
        getApplicationContext(),
        testId,
        /* decodingFormat= */ MP4_ASSET_1080P_5_SECOND_HLG10_FORMAT,
        /* encodingFormat= */ null)) {
      return;
    }

    Context context = ApplicationProvider.getApplicationContext();

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setHdrMode(TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                    .build())
            .build();
    try {
      TransformationTestResult transformationTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_5_SECOND_HLG10)));
      Log.i(TAG, "Tone mapped.");
      assertFileHasColorTransfer(transformationTestResult.filePath, C.COLOR_TRANSFER_SDR);
    } catch (TransformationException exception) {
      Log.i(TAG, checkNotNull(exception.getCause()).toString());
      if (exception.errorCode != TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
        throw exception;
      }
    }
  }
}
