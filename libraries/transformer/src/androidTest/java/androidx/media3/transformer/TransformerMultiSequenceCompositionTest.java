/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.msToUs;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.effect.AlphaScale;
import androidx.media3.effect.Contrast;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.VideoCompositorSettings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for using multiple {@link EditedMediaItemSequence} in a composition. */
@RunWith(AndroidJUnit4.class)
public final class TransformerMultiSequenceCompositionTest {

  // Bitmaps are generated on a Pixel 6 or 7 Pro instead of an emulator, due to an emulator bug.
  // TODO: b/301242589 - Fix this test on the crow emulator, and re-generate bitmaps using the crow
  //  emulator, for consistency with other pixel tests.
  private static final String PNG_ASSET_BASE_PATH =
      "media/bitmap/transformer_multi_sequence_composition_test";

  // The duration of one frame of the 30 FPS test video, in milliseconds.
  private static final long ONE_FRAME_DURATION_MS = 35;
  private static final int EXPORT_WIDTH = 360;
  private static final int EXPORT_HEIGHT = 240;

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void export_withTwoSequencesEachWithOneVideoMediaItem_succeeds() throws Exception {
    String testId = "export_withTwoSequencesEachWithOneVideoMediaItem_succeeds";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }

    Composition composition =
        createComposition(
            /* compositionEffects= */ ImmutableList.of(
                new Contrast(0.1f),
                Presentation.createForWidthAndHeight(
                    EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
            /* firstSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET_URI_STRING,
                    /* effects= */ ImmutableList.of(
                        new AlphaScale(0.5f),
                        new ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(180)
                            .build()))),
            /* secondSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET_URI_STRING, /* effects= */ ImmutableList.of())),
            VideoCompositorSettings.DEFAULT);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withTwoSequencesOneWithVideoOneWithImage_succeeds() throws Exception {
    String testId = "export_withTwoSequencesOneWithVideoOneWithImage_succeeds";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }

    Composition composition =
        createComposition(
            /* compositionEffects= */ ImmutableList.of(
                new Contrast(0.1f),
                Presentation.createForWidthAndHeight(
                    EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
            /* firstSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET_URI_STRING,
                    /* effects= */ ImmutableList.of(
                        new AlphaScale(0.5f),
                        new ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(180)
                            .build()))),
            /* secondSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemOfOneFrameImage(
                    JPG_ASSET_URI_STRING, /* effects= */ ImmutableList.of())),
            VideoCompositorSettings.DEFAULT);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withTwoSequencesWithVideoCompositorSettings_succeeds() throws Exception {
    String testId = "export_withTwoSequencesWithVideoCompositorSettings_succeeds";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }

    VideoCompositorSettings pictureInPictureVideoCompositorSettings =
        new VideoCompositorSettings() {
          @Override
          public Size getOutputSize(List<Size> inputSizes) {
            return inputSizes.get(0);
          }

          @Override
          public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
            if (inputId == 0) {
              // This tests all OverlaySettings builder variables.
              return new OverlaySettings.Builder()
                  .setScale(.25f, .25f)
                  .setOverlayFrameAnchor(1, -1)
                  .setBackgroundFrameAnchor(.9f, -.7f)
                  .build();
            } else {
              return new OverlaySettings.Builder().build();
            }
          }
        };

    Composition composition =
        createComposition(
            /* compositionEffects= */ ImmutableList.of(
                new Contrast(0.1f),
                Presentation.createForWidthAndHeight(
                    EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
            /* firstSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET_URI_STRING,
                    /* effects= */ ImmutableList.of(
                        new AlphaScale(0.5f),
                        new ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(180)
                            .build()))),
            /* secondSequenceMediaItems= */ ImmutableList.of(
                editedMediaItemByClippingVideo(
                    MP4_ASSET_URI_STRING, /* effects= */ ImmutableList.of())),
            pictureInPictureVideoCompositorSettings);

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  private static EditedMediaItem editedMediaItemByClippingVideo(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(
            MediaItem.fromUri(uri)
                .buildUpon()
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(ONE_FRAME_DURATION_MS)
                        .build())
                .build())
        .setRemoveAudio(true)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  private static EditedMediaItem editedMediaItemOfOneFrameImage(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(uri))
        .setRemoveAudio(true)
        .setDurationUs(msToUs(ONE_FRAME_DURATION_MS))
        .setFrameRate((int) (1000 / ONE_FRAME_DURATION_MS))
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  private static Composition createComposition(
      List<Effect> compositionEffects,
      List<EditedMediaItem> firstSequenceMediaItems,
      List<EditedMediaItem> secondSequenceMediaItems,
      VideoCompositorSettings videoCompositorSettings) {

    return new Composition.Builder(
            ImmutableList.of(
                new EditedMediaItemSequence(firstSequenceMediaItems),
                new EditedMediaItemSequence(secondSequenceMediaItems)))
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(), /* videoEffects= */ compositionEffects))
        .setVideoCompositorSettings(videoCompositorSettings)
        .build();
  }

  private static void assertBitmapsMatchExpected(List<Bitmap> actualBitmaps, String testId)
      throws IOException {
    for (int i = 0; i < actualBitmaps.size(); i++) {
      Bitmap actualBitmap = actualBitmaps.get(i);
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ String.valueOf(i), actualBitmap, /* path= */ null);
      String subTestId = testId + "_" + i;
      Bitmap expectedBitmap =
          readBitmap(Util.formatInvariant("%s/%s.png", PNG_ASSET_BASE_PATH, subTestId));
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, subTestId);
      assertWithMessage("For expected bitmap %s.png", subTestId)
          .that(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA);
    }
  }
}
