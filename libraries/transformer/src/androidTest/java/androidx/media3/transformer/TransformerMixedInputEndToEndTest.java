/*
 * Copyright 2023 The Android Open Source Project
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
 *
 */

package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FRAME_COUNT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.PNG_ASSET_URI_STRING;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.effect.Presentation;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer} for cases that cannot be tested using
 * robolectric.
 *
 * <p>This test aims at testing input of {@linkplain VideoFrameProcessor.InputType mixed types of
 * input}.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerMixedInputEndToEndTest {

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void videoEditing_withImageThenVideoInputs_completesWithCorrectFrameCount()
      throws Exception {
    String testId = "videoEditing_withImageThenVideoInputs_completesWithCorrectFrameCount";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 31;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 360);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, buildComposition(imageEditedMediaItem, videoEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(imageFrameCount + MP4_ASSET_FRAME_COUNT);
  }

  @Test
  public void videoEditing_withVideoThenImageInputs_completesWithCorrectFrameCount()
      throws Exception {
    String testId = "videoEditing_withVideoThenImageInputs_completesWithCorrectFrameCount";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 32;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 480);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(testId, buildComposition(videoEditedMediaItem, imageEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(imageFrameCount + MP4_ASSET_FRAME_COUNT);
  }

  @Test
  public void
      videoEditing_withComplexVideoAndImageInputsEndWithVideo_completesWithCorrectFrameCount()
          throws Exception {
    String testId =
        "videoEditing_withComplexVideoAndImageInputsEndWithVideo_completesWithCorrectFrameCount";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 33;
    EditedMediaItem imageEditedMediaItem1 =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem imageEditedMediaItem2 =
        createImageEditedMediaItem(JPG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    EditedMediaItem videoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 360);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                buildComposition(
                    videoEditedMediaItem,
                    videoEditedMediaItem,
                    imageEditedMediaItem1,
                    imageEditedMediaItem2,
                    videoEditedMediaItem,
                    imageEditedMediaItem1,
                    videoEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(3 * imageFrameCount + 4 * MP4_ASSET_FRAME_COUNT);
  }

  @Test
  public void
      videoEditing_withComplexVideoAndImageInputsEndWithImage_completesWithCorrectFrameCount()
          throws Exception {
    String testId =
        "videoEditing_withComplexVideoAndImageInputsEndWithImage_completesWithCorrectFrameCount";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Transformer transformer =
        new Transformer.Builder(context)
            .setEncoderFactory(
                new DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
            .build();

    int imageFrameCount = 34;
    EditedMediaItem imageEditedMediaItem =
        createImageEditedMediaItem(PNG_ASSET_URI_STRING, /* frameCount= */ imageFrameCount);
    // Result of the following command:
    // ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames sample.mp4
    EditedMediaItem videoEditedMediaItem =
        createVideoEditedMediaItem(MP4_ASSET_URI_STRING, /* height= */ 480);
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .run(
                testId,
                buildComposition(
                    imageEditedMediaItem,
                    videoEditedMediaItem,
                    videoEditedMediaItem,
                    imageEditedMediaItem,
                    imageEditedMediaItem,
                    videoEditedMediaItem,
                    imageEditedMediaItem));

    assertThat(result.exportResult.videoFrameCount)
        .isEqualTo(4 * imageFrameCount + 3 * MP4_ASSET_FRAME_COUNT);
  }

  /** Creates an {@link EditedMediaItem} with image, with duration of one second. */
  private static EditedMediaItem createImageEditedMediaItem(String uri, int frameCount) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(uri))
        .setDurationUs(C.MICROS_PER_SECOND)
        .setFrameRate(frameCount)
        .build();
  }

  /**
   * Creates an {@link EditedMediaItem} with video, with audio removed and a {@link Presentation} of
   * specified {@code height}.
   */
  private static EditedMediaItem createVideoEditedMediaItem(String uri, int height) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(uri)))
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(),
                ImmutableList.of(Presentation.createForHeight(height))))
        .setRemoveAudio(true)
        .build();
  }

  private static Composition buildComposition(
      EditedMediaItem editedMediaItem, EditedMediaItem... editedMediaItems) {
    return new Composition.Builder(new EditedMediaItemSequence(editedMediaItem, editedMediaItems))
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(),
                ImmutableList.of(
                    // To ensure that software encoders can encode.
                    Presentation.createForWidthAndHeight(
                        /* width= */ 480, /* height= */ 360, Presentation.LAYOUT_SCALE_TO_FIT))))
        .build();
  }
}
