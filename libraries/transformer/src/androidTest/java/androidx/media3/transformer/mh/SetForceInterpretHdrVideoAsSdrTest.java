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

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_4_SECOND_HDR10;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_4_SECOND_HDR10_FORMAT;
import static androidx.media3.transformer.mh.analysis.FileUtil.assertFileHasColorTransfer;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
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
 * {@link Transformer} instrumentation test for {@linkplain
 * TransformationRequest#forceInterpretHdrVideoAsSdr forcing HDR contents to be interpreted as SDR}.
 */
@RunWith(AndroidJUnit4.class)
public class SetForceInterpretHdrVideoAsSdrTest {
  public static final String TAG = "SetForceInterpretHdrVideoAsSdrTest";

  @Test
  public void forceInterpretHdrVideoAsSdrTest_hdr10File_transformsOrThrows() throws Exception {
    String testId = "forceInterpretHdrVideoAsSdrTest_hdr10File_transformsOrThrows";
    Context context = ApplicationProvider.getApplicationContext();

    if (AndroidTestUtil.skipAndLogIfInsufficientCodecSupport(
        context,
        testId,
        /* decodingFormat= */ MP4_ASSET_1080P_4_SECOND_HDR10_FORMAT,
        /* encodingFormat= */ null)) {
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .experimental_setForceInterpretHdrVideoAsSdr(true)
                    .build())
            .build();
    try {
      TransformationTestResult transformationTestResult =
          new TransformerAndroidTestRunner.Builder(context, transformer)
              .build()
              .run(testId, MediaItem.fromUri(Uri.parse(MP4_ASSET_1080P_4_SECOND_HDR10)));
      assertFileHasColorTransfer(transformationTestResult.filePath, C.COLOR_TRANSFER_SDR);
      Log.i(TAG, "Transformed.");
    } catch (TransformationException exception) {
      if (exception.errorCode != TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
          && exception.errorCode != TransformationException.ERROR_CODE_HDR_DECODING_UNSUPPORTED) {
        throw exception;
      }
    }
  }
}
