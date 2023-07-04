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

import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.MimeTypes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TransformationRequest}. */
@RunWith(AndroidJUnit4.class)
public class TransformationRequestTest {

  @Test
  public void buildUponTransformationRequest_createsEqualTransformationRequest() {
    TransformationRequest request = createTestTransformationRequest();
    assertThat(request.buildUpon().build()).isEqualTo(request);
  }

  private static TransformationRequest createTestTransformationRequest() {
    return new TransformationRequest.Builder()
        .setResolution(720)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        .build();
  }
}
