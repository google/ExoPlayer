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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer} for cases that cannot be tested using
 * robolectric.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerEndToEndTest {

  @Test
  public void videoEditing_completesWithConsistentFrameCount() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setResolution(480).build())
            .setEncoderFactory(
                new DefaultEncoderFactory(EncoderSelector.DEFAULT, /* enableFallback= */ false))
            .build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    int expectedFrameCount = 30;

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                /* testId= */ "videoEditing_completesWithConsistentFrameCount",
                MP4_ASSET_URI_STRING);

    assertThat(result.transformationResult.videoFrameCount).isEqualTo(expectedFrameCount);
  }

  @Test
  public void videoOnly_completesWithConsistentDuration() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Transformer transformer =
        new Transformer.Builder(context)
            .setRemoveAudio(true)
            .setTransformationRequest(
                new TransformationRequest.Builder().setResolution(480).build())
            .setEncoderFactory(
                new DefaultEncoderFactory(EncoderSelector.DEFAULT, /* enableFallback= */ false))
            .build();
    long expectedDurationMs = 967;

    TransformationTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(/* testId= */ "videoOnly_completesWithConsistentDuration", MP4_ASSET_URI_STRING);

    assertThat(result.transformationResult.durationMs).isEqualTo(expectedDurationMs);
  }
}
