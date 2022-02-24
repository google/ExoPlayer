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
package com.google.android.exoplayer2.transformer.mh;

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.runTransformer;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.graphics.Matrix;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.transformer.AndroidTestUtil;
import com.google.android.exoplayer2.transformer.TransformationRequest;
import com.google.android.exoplayer2.transformer.Transformer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.HashSet;
import java.util.Set;
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
    Matrix transformationMatrix = new Matrix();
    transformationMatrix.postTranslate((float) 0.1, (float) 0.1);
    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setVideoMimeType(MimeTypes.VIDEO_H265)
                    .setTransformationMatrix(transformationMatrix)
                    .setAudioMimeType(MimeTypes.AUDIO_AMR_NB)
                    .build())
            .build();

    Set<Long> differentOutputSizesBytes = new HashSet<>();
    for (int i = 0; i < TRANSCODE_COUNT; i++) {
      // Use a long video in case an error occurs a while after the start of the video.
      AndroidTestUtil.TransformationResult result =
          runTransformer(
              context,
              /* testId= */ "repeatedTranscode_givesConsistentLengthOutput_" + i,
              transformer,
              AndroidTestUtil.REMOTE_MP4_10_SECONDS_URI_STRING,
              /* timeoutSeconds= */ 120);
      differentOutputSizesBytes.add(Assertions.checkNotNull(result.fileSizeBytes));
    }

    assertWithMessage(
            "Different transcoding output sizes detected. Sizes: " + differentOutputSizesBytes)
        .that(differentOutputSizesBytes.size())
        .isEqualTo(1);
  }

  @Test
  public void repeatedTranscodeNoAudio_givesConsistentLengthOutput() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Matrix transformationMatrix = new Matrix();
    transformationMatrix.postTranslate((float) 0.1, (float) 0.1);
    Transformer transformer =
        new Transformer.Builder(context)
            .setRemoveAudio(true)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setVideoMimeType(MimeTypes.VIDEO_H265)
                    .setTransformationMatrix(transformationMatrix)
                    .build())
            .build();

    Set<Long> differentOutputSizesBytes = new HashSet<>();
    for (int i = 0; i < TRANSCODE_COUNT; i++) {
      // Use a long video in case an error occurs a while after the start of the video.
      AndroidTestUtil.TransformationResult result =
          runTransformer(
              context,
              /* testId= */ "repeatedTranscodeNoAudio_givesConsistentLengthOutput_" + i,
              transformer,
              AndroidTestUtil.REMOTE_MP4_10_SECONDS_URI_STRING,
              /* timeoutSeconds= */ 120);
      differentOutputSizesBytes.add(Assertions.checkNotNull(result.fileSizeBytes));
    }

    assertWithMessage(
            "Different transcoding output sizes detected. Sizes: " + differentOutputSizesBytes)
        .that(differentOutputSizesBytes.size())
        .isEqualTo(1);
  }

  @Test
  public void repeatedTranscodeNoVideo_givesConsistentLengthOutput() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setRemoveVideo(true)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setAudioMimeType(MimeTypes.AUDIO_AMR_NB)
                    .build())
            .build();

    Set<Long> differentOutputSizesBytes = new HashSet<>();
    for (int i = 0; i < TRANSCODE_COUNT; i++) {
      // Use a long video in case an error occurs a while after the start of the video.
      AndroidTestUtil.TransformationResult result =
          runTransformer(
              context,
              /* testId= */ "repeatedTranscodeNoVideo_givesConsistentLengthOutput_" + i,
              transformer,
              AndroidTestUtil.REMOTE_MP4_10_SECONDS_URI_STRING,
              /* timeoutSeconds= */ 120);
      differentOutputSizesBytes.add(Assertions.checkNotNull(result.fileSizeBytes));
    }

    assertWithMessage(
            "Different transcoding output sizes detected. Sizes: " + differentOutputSizesBytes)
        .that(differentOutputSizesBytes.size())
        .isEqualTo(1);
  }
}
