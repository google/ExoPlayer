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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Matrix;
import androidx.media3.common.MimeTypes;
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

  private static final String VP9_VIDEO_URI_STRING = "asset:///media/vp9/bear-vp9.webm";
  private static final String AVC_VIDEO_URI_STRING = "asset:///media/mp4/sample.mp4";

  @Test
  public void videoTranscoding_completesWithConsistentFrameCount() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    FrameCountingMuxer.Factory muxerFactory =
        new FrameCountingMuxer.Factory(new FrameworkMuxer.Factory());
    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).build())
            .setMuxerFactory(muxerFactory)
            .setEncoderFactory(
                new DefaultEncoderFactory(EncoderSelector.DEFAULT, /* enableFallback= */ false))
            .build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames bear-vp9.webm
    int expectedFrameCount = 82;

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(
            /* testId= */ "videoTranscoding_completesWithConsistentFrameCount",
            VP9_VIDEO_URI_STRING);

    FrameCountingMuxer frameCountingMuxer =
        checkNotNull(muxerFactory.getLastFrameCountingMuxerCreated());
    assertThat(frameCountingMuxer.getFrameCount()).isEqualTo(expectedFrameCount);
  }

  @Test
  public void videoEditing_completesWithConsistentFrameCount() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    Matrix transformationMatrix = new Matrix();
    transformationMatrix.postTranslate(/* dx= */ .2f, /* dy= */ .1f);
    FrameCountingMuxer.Factory muxerFactory =
        new FrameCountingMuxer.Factory(new FrameworkMuxer.Factory());
    Transformer transformer =
        new Transformer.Builder(context)
            .setTransformationRequest(
                new TransformationRequest.Builder()
                    .setTransformationMatrix(transformationMatrix)
                    .build())
            .setMuxerFactory(muxerFactory)
            .setEncoderFactory(
                new DefaultEncoderFactory(EncoderSelector.DEFAULT, /* enableFallback= */ false))
            .build();
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    int expectedFrameCount = 30;

    new TransformerAndroidTestRunner.Builder(context, transformer)
        .build()
        .run(/* testId= */ "videoEditing_completesWithConsistentFrameCount", AVC_VIDEO_URI_STRING);

    FrameCountingMuxer frameCountingMuxer =
        checkNotNull(muxerFactory.getLastFrameCountingMuxerCreated());
    assertThat(frameCountingMuxer.getFrameCount()).isEqualTo(expectedFrameCount);
  }
}
