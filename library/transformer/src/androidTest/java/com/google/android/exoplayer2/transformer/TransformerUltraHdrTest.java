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

import static com.google.android.exoplayer2.testutil.TestUtil.retrieveTrackFormat;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.JPG_ASSET_URI_STRING;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.ULTRA_HDR_URI_STRING;
import static com.google.android.exoplayer2.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.NO_EFFECT;
import static com.google.android.exoplayer2.transformer.SequenceEffectTestUtil.oneFrameFromImage;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSourceBitmapLoader;
import com.google.android.exoplayer2.util.BitmapLoader;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Tests for Ultra HDR support in Transformer that can run on an emulator.
 *
 * <p>See {@code TransformerMhUltraHdrPixelTest} for other UltraHdr tests.
 */
@RunWith(AndroidJUnit4.class)
public final class TransformerUltraHdrTest {

  @Rule public final TestName testName = new TestName();
  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void exportUltraHdrImage_withUltraHdrEnabledOnUnsupportedApiLevel_fallbackToExportSdr()
      throws Exception {
    assumeTrue(Util.SDK_INT < 34);
    Composition composition =
        createUltraHdrComposition(
            /* tonemap= */ false, oneFrameFromImage(ULTRA_HDR_URI_STRING, NO_EFFECT));

    // Downscale source bitmap to avoid "video encoding format not supported" errors on emulators.
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, createDownscalingTransformer())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT709);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
  }

  @Test
  public void exportUltraHdrImage_withUltraHdrAndTonemappingEnabled_exportsSdr() throws Exception {
    Composition composition =
        createUltraHdrComposition(
            /* tonemap= */ true, oneFrameFromImage(ULTRA_HDR_URI_STRING, NO_EFFECT));

    // Downscale source bitmap to avoid "video encoding format not supported" errors on emulators.
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, createDownscalingTransformer())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT709);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
  }

  @Test
  public void exportUltraHdrImage_withUltraHdrDisabled_exportsSdr() throws Exception {
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence(oneFrameFromImage(ULTRA_HDR_URI_STRING, NO_EFFECT)))
            .build();

    // Downscale source bitmap to avoid "video encoding format not supported" errors on emulators.
    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, createDownscalingTransformer())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT709);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
  }

  @Test
  public void exportNonUltraHdrImage_withUltraHdrEnabled_exportsSdr() throws Exception {
    Composition composition =
        createUltraHdrComposition(
            /* tonemap= */ false, oneFrameFromImage(JPG_ASSET_URI_STRING, NO_EFFECT));

    ExportTestResult result =
        new TransformerAndroidTestRunner.Builder(context, new Transformer.Builder(context).build())
            .build()
            .run(testId, composition);

    assertThat(result.filePath).isNotNull();
    ColorInfo colorInfo =
        retrieveTrackFormat(context, result.filePath, C.TRACK_TYPE_VIDEO).colorInfo;
    assertThat(colorInfo.colorSpace).isEqualTo(C.COLOR_SPACE_BT709);
    assertThat(colorInfo.colorTransfer).isEqualTo(C.COLOR_TRANSFER_SDR);
  }

  private static Composition createUltraHdrComposition(
      boolean tonemap, EditedMediaItem editedMediaItem, EditedMediaItem... editedMediaItems) {
    Composition.Builder builder =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem, editedMediaItems))
            .experimentalSetRetainHdrFromUltraHdrImage(true);
    if (tonemap) {
      builder.setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
    }
    return builder.build();
  }

  private Transformer createDownscalingTransformer() {
    BitmapLoader downscalingBitmapLoader =
        new BitmapLoader() {

          static final int DOWNSCALED_WIDTH_HEIGHT = 120;
          final BitmapLoader bitmapLoader;

          {
            bitmapLoader = new DataSourceBitmapLoader(context);
          }

          @Override
          public boolean supportsMimeType(String mimeType) {
            return bitmapLoader.supportsMimeType(mimeType);
          }

          @Override
          public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
            return bitmapLoader.decodeBitmap(data);
          }

          @Override
          public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
            SettableFuture<Bitmap> outputFuture = SettableFuture.create();
            try {
              Bitmap bitmap =
                  Bitmap.createScaledBitmap(
                      bitmapLoader.loadBitmap(uri).get(),
                      DOWNSCALED_WIDTH_HEIGHT,
                      DOWNSCALED_WIDTH_HEIGHT,
                      /* filter= */ true);
              outputFuture.set(bitmap);
              return outputFuture;
            } catch (ExecutionException | InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        };

    return new Transformer.Builder(context)
        .setAssetLoaderFactory(new DefaultAssetLoaderFactory(context, downscalingBitmapLoader))
        .build();
  }
}
