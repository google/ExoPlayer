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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.JPG_PORTRAIT_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.MP4_PORTRAIT_ASSET_URI_STRING;
import static androidx.media3.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Util;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for using different {@linkplain Effect effects} for {@link MediaItem MediaItems} in one
 * {@link EditedMediaItemSequence} .
 */
@RunWith(AndroidJUnit4.class)
public final class TransformerSequenceEffectTest {

  private static final ImmutableList<Effect> NO_EFFECT = ImmutableList.of();
  private static final String PNG_ASSET_BASE_PATH = "media/bitmap/transformer_sequence_effect_test";
  private static final String OVERLAY_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";
  private static final int EXPORT_WIDTH = 360;
  private static final int EXPORT_HEIGHT = 240;

  private final Context context = ApplicationProvider.getApplicationContext();

  @Test
  public void export_withNoCompositionPresentationAndWithPerMediaItemEffects() throws Exception {
    String testId = "export_withNoCompositionPresentationAndWithPerMediaItemEffects";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    OverlayEffect overlayEffect = createOverlayEffect();
    Composition composition =
        createComposition(
            /* presentation= */ null,
            oneFrameFromVideo(
                MP4_ASSET_URI_STRING,
                ImmutableList.of(
                    Presentation.createForWidthAndHeight(
                        EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT))),
            oneFrameFromImage(
                JPG_ASSET_URI_STRING,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(72).build(),
                    overlayEffect)),
            oneFrameFromImage(JPG_ASSET_URI_STRING, NO_EFFECT),
            // Transition to a different aspect ratio.
            oneFrameFromImage(
                JPG_ASSET_URI_STRING,
                ImmutableList.of(
                    Presentation.createForWidthAndHeight(
                        EXPORT_WIDTH / 2, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT),
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(90).build(),
                    overlayEffect)));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withCompositionPresentationAndWithPerMediaItemEffects() throws Exception {
    String testId = "export_withCompositionPresentationAndWithPerMediaItemEffects";

    // Reference: b/296225823#comment5
    assumeFalse(
        "Some older MediaTek encoders have a pixel alignment of 16, which results in a 360 pixel"
            + " width being re-scaled to 368.",
        SDK_INT == 27
            && (Ascii.equalsIgnoreCase(Util.MODEL, "redmi 6a")
                || Ascii.equalsIgnoreCase(Util.MODEL, "vivo 1820")));

    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Composition composition =
        createComposition(
            Presentation.createForWidthAndHeight(
                EXPORT_WIDTH, /* height= */ EXPORT_WIDTH, Presentation.LAYOUT_SCALE_TO_FIT),
            oneFrameFromImage(
                JPG_ASSET_URI_STRING,
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(90).build(),
                    Presentation.createForWidthAndHeight(
                        EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT))),
            oneFrameFromImage(JPG_ASSET_URI_STRING, NO_EFFECT),
            oneFrameFromVideo(
                MP4_ASSET_URI_STRING, ImmutableList.of(RgbFilter.createInvertedFilter())),
            oneFrameFromVideo(
                MP4_ASSET_URI_STRING,
                ImmutableList.of(
                    Presentation.createForWidthAndHeight(
                        EXPORT_WIDTH / 2, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT),
                    createOverlayEffect())));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withCompositionPresentationAndNoVideoEffects() throws Exception {
    String testId = "export_withCompositionPresentationAndNoVideoEffects";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            oneFrameFromImage(JPG_ASSET_URI_STRING, NO_EFFECT),
            oneFrameFromVideo(MP4_PORTRAIT_ASSET_URI_STRING, NO_EFFECT),
            oneFrameFromVideo(MP4_ASSET_URI_STRING, NO_EFFECT),
            oneFrameFromImage(JPG_PORTRAIT_ASSET_URI_STRING, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withCompositionPresentationAndNoVideoEffectsForFirstMediaItem()
      throws Exception {
    String testId = "export_withCompositionPresentationAndNoVideoEffectsForFirstMediaItem";
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            oneFrameFromVideo(MP4_ASSET_URI_STRING, NO_EFFECT),
            oneFrameFromVideo(
                MP4_PORTRAIT_ASSET_URI_STRING, ImmutableList.of(RgbFilter.createInvertedFilter())));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpected(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  private static OverlayEffect createOverlayEffect() throws IOException {
    return new OverlayEffect(
        ImmutableList.of(
            BitmapOverlay.createStaticBitmapOverlay(readBitmap(OVERLAY_PNG_ASSET_PATH))));
  }

  private static Composition createComposition(
      @Nullable Presentation presentation,
      EditedMediaItem editedMediaItem,
      EditedMediaItem... editedMediaItems) {
    Composition.Builder builder =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem, editedMediaItems));
    if (presentation != null) {
      builder.setEffects(
          new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.of(presentation)));
    }
    return builder.build();
  }

  private static EditedMediaItem oneFrameFromVideo(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(
            MediaItem.fromUri(uri)
                .buildUpon()
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                        // Clip to only the first frame.
                        .setEndPositionMs(50)
                        .build())
                .build())
        .setRemoveAudio(true)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  private static EditedMediaItem oneFrameFromImage(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(uri))
        // 50ms for a 20-fps video is one frame.
        .setFrameRate(20)
        .setDurationUs(50_000)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  private static void assertBitmapsMatchExpected(List<Bitmap> actualBitmaps, String testId)
      throws IOException {
    for (int i = 0; i < actualBitmaps.size(); i++) {
      Bitmap actualBitmap = actualBitmaps.get(i);
      String subTestId = testId + "_" + i;
      Bitmap expectedBitmap =
          readBitmap(Util.formatInvariant("%s/%s.png", PNG_ASSET_BASE_PATH, subTestId));

      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ String.valueOf(i), actualBitmap, /* path= */ null);
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, subTestId);
      assertWithMessage("For expected bitmap %s.png", subTestId)
          .that(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA);
    }
  }
}
