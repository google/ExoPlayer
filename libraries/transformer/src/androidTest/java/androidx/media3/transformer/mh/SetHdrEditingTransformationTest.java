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

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_1080P_1_SECOND_HDR10_VIDEO_SDR_CONTAINER;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.transformer.TransformationException;
import androidx.media3.transformer.TransformationRequest;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.TransformerAndroidTestRunner;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** {@link Transformer} instrumentation test for applying an HDR frame edit. */
@RunWith(AndroidJUnit4.class)
public class SetHdrEditingTransformationTest {
  @Test
  public void videoDecoderUnexpectedColorInfo_completesWithError() {
    Context context = ApplicationProvider.getApplicationContext();
    if (Util.SDK_INT < 24) {
      return;
    }

    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().experimental_setEnableHdrEditing(true).build())
            .build();
    TransformationException exception =
        assertThrows(
            TransformationException.class,
            () ->
                new TransformerAndroidTestRunner.Builder(context, transformer)
                    .build()
                    .run(
                        /* testId= */ "videoDecoderUnexpectedColorInfo_completesWithError",
                        MediaItem.fromUri(
                            Uri.parse(MP4_ASSET_1080P_1_SECOND_HDR10_VIDEO_SDR_CONTAINER))));
    assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
    assertThat(exception.errorCode).isEqualTo(TransformationException.ERROR_CODE_DECODING_FAILED);
  }
}
