/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.runTransformer;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests repeated transcoding operations (as a stress test and to help reproduce flakiness). */
@RunWith(AndroidJUnit4.class)
@Ignore("Internal - b/206917996")
public final class RepeatedTranscodeTransformationTest {

  private static final int TRANSCODE_COUNT = 10;

  @Test
  public void repeatedTranscode_givesConsistentLengthOutput() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder()
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setContext(context)
            .build();

    long previousOutputSizeBytes = C.LENGTH_UNSET;
    for (int i = 0; i < TRANSCODE_COUNT; i++) {
      // Use a long video in case an error occurs a while after the start of the video.
      long outputSizeBytes =
          runTransformer(context, transformer, AndroidTestUtil.REMOTE_MP4_10_SECONDS_URI_STRING)
              .outputSizeBytes;
      if (previousOutputSizeBytes != C.LENGTH_UNSET) {
        assertWithMessage("Unexpected output size on transcode " + i + " out of " + TRANSCODE_COUNT)
            .that(outputSizeBytes)
            .isEqualTo(previousOutputSizeBytes);
      }
      previousOutputSizeBytes = outputSizeBytes;
    }
  }
}
