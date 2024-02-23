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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.BT601_ASSET_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.BT601_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.JPG_PORTRAIT_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_FORMAT;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.MP4_PORTRAIT_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.extractBitmapsFromVideo;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.assertBitmapsMatchExpectedAndSave;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.clippedVideo;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.createComposition;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.oneFrameFromImage;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.SDK_INT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.effect.BitmapOverlay;
import com.google.android.exoplayer2.effect.OverlayEffect;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.effect.RgbFilter;
import com.google.android.exoplayer2.effect.ScaleAndRotateTransformation;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for using different {@linkplain Effect effects} for {@link MediaItem MediaItems} in one
 * {@link EditedMediaItemSequence}.
 */
@RunWith(AndroidJUnit4.class)
public final class TransformerSequenceEffectTest {

  private static final ImmutableList<Effect> NO_EFFECT = ImmutableList.of();
  private static final String OVERLAY_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";
  private static final int EXPORT_WIDTH = 360;
  private static final int EXPORT_HEIGHT = 240;

  private final Context context = ApplicationProvider.getApplicationContext();
  @Rule public final TestName testName = new TestName();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void export_withNoCompositionPresentationAndWithPerMediaItemEffects() throws Exception {
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
            clippedVideo(
                MP4_ASSET_URI_STRING,
                ImmutableList.of(
                    Presentation.createForWidthAndHeight(
                        EXPORT_WIDTH, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT)),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
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
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withCompositionPresentationAndWithPerMediaItemEffects() throws Exception {
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
            clippedVideo(
                MP4_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_ASSET_URI_STRING,
                ImmutableList.of(
                    Presentation.createForWidthAndHeight(
                        EXPORT_WIDTH / 2, EXPORT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT),
                    createOverlayEffect()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withCompositionPresentationAndNoVideoEffects() throws Exception {
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
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING, NO_EFFECT, SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(MP4_ASSET_URI_STRING, NO_EFFECT, SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            oneFrameFromImage(JPG_PORTRAIT_ASSET_URI_STRING, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withCompositionPresentationAndNoVideoEffectsForFirstMediaItem()
      throws Exception {
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
            clippedVideo(MP4_ASSET_URI_STRING, NO_EFFECT, SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(
                MP4_PORTRAIT_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withBt601AndBt709MediaItems() throws Exception {
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ BT601_ASSET_FORMAT, /* outputFormat= */ null)) {
      return;
    }
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                BT601_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            clippedVideo(MP4_ASSET_URI_STRING, NO_EFFECT, SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  @Test
  public void export_withBt601VideoAndBt709ImageMediaItems() throws Exception {
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context,
        testId,
        /* inputFormat= */ MP4_ASSET_FORMAT,
        /* outputFormat= */ MP4_ASSET_FORMAT)) {
      return;
    }
    if (AndroidTestUtil.skipAndLogIfFormatsUnsupported(
        context, testId, /* inputFormat= */ BT601_ASSET_FORMAT, /* outputFormat= */ null)) {
      return;
    }
    Composition composition =
        createComposition(
            Presentation.createForHeight(EXPORT_HEIGHT),
            clippedVideo(
                BT601_ASSET_URI_STRING,
                ImmutableList.of(RgbFilter.createInvertedFilter()),
                SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS),
            oneFrameFromImage(JPG_ASSET_URI_STRING, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    assertBitmapsMatchExpectedAndSave(
        extractBitmapsFromVideo(context, checkNotNull(result.filePath)), testId);
  }

  private static OverlayEffect createOverlayEffect() throws IOException {
    return new OverlayEffect(
        ImmutableList.of(
            BitmapOverlay.createStaticBitmapOverlay(readBitmap(OVERLAY_PNG_ASSET_PATH))));
  }
}
