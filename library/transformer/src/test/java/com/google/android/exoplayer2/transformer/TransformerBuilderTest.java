/*
 * Copyright 2020 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Transformer.Builder}. */
@RunWith(AndroidJUnit4.class)
public class TransformerBuilderTest {

  @Test
  public void build_removeAudioAndVideo_throws() {
    Context context = ApplicationProvider.getApplicationContext();

    assertThrows(
        IllegalStateException.class,
        () -> new Transformer.Builder(context).setRemoveAudio(true).setRemoveVideo(true).build());
  }

  @Test
  public void build_withUnsupportedAudioMimeType_throws() {
    Context context = ApplicationProvider.getApplicationContext();
    TransformationRequest transformationRequest =
        new TransformationRequest.Builder().setAudioMimeType(MimeTypes.AUDIO_UNKNOWN).build();

    assertThrows(
        IllegalStateException.class,
        () ->
            new Transformer.Builder(context)
                .setTransformationRequest(transformationRequest)
                .build());
  }

  @Test
  public void build_withUnsupportedVideoMimeType_throws() {
    Context context = ApplicationProvider.getApplicationContext();
    TransformationRequest transformationRequest =
        new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_UNKNOWN).build();

    assertThrows(
        IllegalStateException.class,
        () ->
            new Transformer.Builder(context)
                .setTransformationRequest(transformationRequest)
                .build());
  }
}
