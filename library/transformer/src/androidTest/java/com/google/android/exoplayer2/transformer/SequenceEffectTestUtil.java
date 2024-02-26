/*
 * Copyright 2024 The Android Open Source Project
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

import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static com.google.android.exoplayer2.testutil.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertWithMessage;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;

/** Utility class for checking testing {@link EditedMediaItemSequence} instances. */
public final class SequenceEffectTestUtil {
  private static final String PNG_ASSET_BASE_PATH =
      "test-generated-goldens/transformer_sequence_effect_test";
  public static final long SINGLE_30_FPS_VIDEO_FRAME_THRESHOLD_MS = 50;

  private SequenceEffectTestUtil() {}

  /**
   * Creates a {@link Composition} with the specified {@link Presentation} and {@link
   * EditedMediaItem} instances.
   */
  public static Composition createComposition(
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

  /**
   * Creates an {@link EditedMediaItem} with a video at {@code uri} clipped to the {@code
   * endPositionMs}, with {@code effects} applied.
   *
   * <p>This may be used to, for example, clip to only the first frame of a video.
   */
  public static EditedMediaItem clippedVideo(String uri, List<Effect> effects, long endPositionMs) {
    return new EditedMediaItem.Builder(
            MediaItem.fromUri(uri)
                .buildUpon()
                .setClippingConfiguration(
                    new MediaItem.ClippingConfiguration.Builder()
                        .setEndPositionMs(endPositionMs)
                        .build())
                .build())
        .setRemoveAudio(true)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  /**
   * Creates an {@link EditedMediaItem} with an image at {@code uri}, shown once, with {@code
   * effects} applied.
   */
  public static EditedMediaItem oneFrameFromImage(String uri, List<Effect> effects) {
    return new EditedMediaItem.Builder(MediaItem.fromUri(uri))
        // 50ms for a 20-fps video is one frame.
        .setFrameRate(20)
        .setDurationUs(50_000)
        .setEffects(
            new Effects(/* audioProcessors= */ ImmutableList.of(), ImmutableList.copyOf(effects)))
        .build();
  }

  /**
   * Assert that the bitmaps output in {@link #PNG_ASSET_BASE_PATH} match those written in {code
   * actualBitmaps}.
   *
   * <p>Also saves {@code actualBitmaps} bitmaps, in case they differ from expected bitmaps, stored
   * at {@link #PNG_ASSET_BASE_PATH}/{@code testId}_id.png.
   */
  public static void assertBitmapsMatchExpectedAndSave(List<Bitmap> actualBitmaps, String testId)
      throws IOException {
    for (int i = 0; i < actualBitmaps.size(); i++) {
      maybeSaveTestBitmap(
          testId, /* bitmapLabel= */ String.valueOf(i), actualBitmaps.get(i), /* path= */ null);
    }

    for (int i = 0; i < actualBitmaps.size(); i++) {
      String subTestId = testId + "_" + i;
      String expectedPath = Util.formatInvariant("%s/%s.png", PNG_ASSET_BASE_PATH, subTestId);
      Bitmap expectedBitmap = readBitmap(expectedPath);

      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(
              expectedBitmap, actualBitmaps.get(i), subTestId);
      assertWithMessage("For expected bitmap " + expectedPath)
          .that(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_LUMA);
    }
  }
}
