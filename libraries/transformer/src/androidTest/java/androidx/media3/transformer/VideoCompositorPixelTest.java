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

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.Bitmap;
import android.opengl.EGLContext;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.VideoCompositor;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Pixel test for {@link VideoCompositor} compositing 2 input frames into 1 output frame. */
@RunWith(Parameterized.class)
public final class VideoCompositorPixelTest {
  private @MonotonicNonNull VideoFrameProcessorTestRunner inputVfpTestRunner1;
  private @MonotonicNonNull VideoFrameProcessorTestRunner inputVfpTestRunner2;

  private static final String ORIGINAL_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";
  private static final String GRAYSCALE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/grayscale_media3test.png";
  private static final String ROTATE180_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/rotate180_media3test.png";
  private static final String GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/grayscaleAndRotate180Composite.png";

  private static final Effect ROTATE_180 =
      new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build();
  private static final Effect GRAYSCALE = RgbFilter.createGrayscaleFilter();

  @Parameterized.Parameters(name = "useSharedExecutor={0}")
  public static ImmutableList<Boolean> useSharedExecutor() {
    return ImmutableList.of(true, false);
  }

  @Parameterized.Parameter public boolean useSharedExecutor;

  public @Nullable ExecutorService executorService;

  @After
  public void tearDown() {
    if (inputVfpTestRunner1 != null) {
      inputVfpTestRunner1.release();
    }
    if (inputVfpTestRunner2 != null) {
      inputVfpTestRunner2.release();
    }

    if (executorService != null) {
      try {
        executorService.shutdown();
        if (!executorService.awaitTermination(/* timeout= */ 5000, MILLISECONDS)) {
          throw new IllegalStateException("Missed shutdown timeout.");
        }
      } catch (InterruptedException unexpected) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(unexpected);
      }
    }
  }

  @Test
  public void compositeTwoFrames_matchesExpected() throws Exception {
    String testId =
        "compositeTwoFrames_matchesExpected[useSharedExecutor=" + useSharedExecutor + "]";
    executorService = useSharedExecutor ? Util.newSingleThreadExecutor("Effect:GlThread") : null;

    // Arrange VideoCompositor and VideoFrameProcessor instances.
    EGLContext sharedEglContext = AndroidTestUtil.createOpenGlObjects();
    GlObjectsProvider sharedGlObjectsProvider = new DefaultGlObjectsProvider(sharedEglContext);
    AtomicReference<Bitmap> compositedOutputBitmap = new AtomicReference<>();
    VideoCompositor videoCompositor =
        new VideoCompositor(
            getApplicationContext(),
            sharedGlObjectsProvider,
            /* textureOutputListener= */ (outputTexture,
                presentationTimeUs,
                releaseOutputTextureCallback,
                syncObject) -> {
              try {
                if (useSharedExecutor) {
                  GlUtil.deleteSyncObject(syncObject);
                } else {
                  GlUtil.awaitSyncObject(syncObject);
                }
                compositedOutputBitmap.set(
                    BitmapPixelTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer(
                        outputTexture.getWidth(), outputTexture.getHeight()));
              } catch (GlUtil.GlException e) {
                throw VideoFrameProcessingException.from(e);
              } finally {
                releaseOutputTextureCallback.release(presentationTimeUs);
              }
            },
            /* textureOutputCapacity= */ 1);
    TextureBitmapReader inputTextureBitmapReader1 = new TextureBitmapReader();
    VideoFrameProcessorTestRunner inputVfpTestRunner1 =
        getFrameProcessorTestRunnerBuilder(
                testId,
                inputTextureBitmapReader1,
                videoCompositor,
                executorService,
                sharedGlObjectsProvider)
            .setEffects(GRAYSCALE)
            .build();
    this.inputVfpTestRunner1 = inputVfpTestRunner1;
    TextureBitmapReader inputTextureBitmapReader2 = new TextureBitmapReader();
    VideoFrameProcessorTestRunner inputVfpTestRunner2 =
        getFrameProcessorTestRunnerBuilder(
                testId,
                inputTextureBitmapReader2,
                videoCompositor,
                executorService,
                sharedGlObjectsProvider)
            .setEffects(ROTATE_180)
            .build();
    this.inputVfpTestRunner2 = inputVfpTestRunner2;

    // Queue 1 input bitmap from each input VideoFrameProcessor source.
    inputVfpTestRunner1.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ 1 * C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0,
        /* frameRate= */ 1);
    inputVfpTestRunner1.endFrameProcessing();
    inputVfpTestRunner2.queueInputBitmap(
        readBitmap(ORIGINAL_PNG_ASSET_PATH),
        /* durationUs= */ 1 * C.MICROS_PER_SECOND,
        /* offsetToAddUs= */ 0,
        /* frameRate= */ 1);
    inputVfpTestRunner2.endFrameProcessing();

    // Check that VideoFrameProcessor and VideoCompositor outputs match expected bitmaps.
    Bitmap actualCompositorInputBitmap1 = checkNotNull(inputTextureBitmapReader1).getBitmap();
    saveAndAssertBitmapMatchesExpected(
        testId,
        actualCompositorInputBitmap1,
        /* actualBitmapLabel= */ "actualCompositorInputBitmap1",
        GRAYSCALE_PNG_ASSET_PATH);
    Bitmap actualCompositorInputBitmap2 = checkNotNull(inputTextureBitmapReader2).getBitmap();
    saveAndAssertBitmapMatchesExpected(
        testId,
        actualCompositorInputBitmap2,
        /* actualBitmapLabel= */ "actualCompositorInputBitmap2",
        ROTATE180_PNG_ASSET_PATH);
    Bitmap compositorOutputBitmap = compositedOutputBitmap.get();
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorOutputBitmap,
        /* actualBitmapLabel= */ "compositorOutputBitmap",
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  private void saveAndAssertBitmapMatchesExpected(
      String testId, Bitmap actualBitmap, String actualBitmapLabel, String expectedBitmapAssetPath)
      throws IOException {
    maybeSaveTestBitmap(testId, actualBitmapLabel, actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            readBitmap(expectedBitmapAssetPath), actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }

  private static VideoFrameProcessorTestRunner.Builder getFrameProcessorTestRunnerBuilder(
      String testId,
      TextureBitmapReader textureBitmapReader,
      VideoCompositor videoCompositor,
      @Nullable ExecutorService executorService,
      GlObjectsProvider glObjectsProvider) {
    int inputId = videoCompositor.registerInputSource();
    VideoFrameProcessor.Factory defaultVideoFrameProcessorFactory =
        new DefaultVideoFrameProcessor.Factory.Builder()
            .setGlObjectsProvider(glObjectsProvider)
            .setTextureOutput(
                /* textureOutputListener= */ (GlTextureInfo outputTexture,
                    long presentationTimeUs,
                    DefaultVideoFrameProcessor.ReleaseOutputTextureCallback
                        releaseOutputTextureCallback,
                    long syncObject) -> {
                  GlUtil.awaitSyncObject(syncObject);
                  textureBitmapReader.readBitmap(outputTexture, presentationTimeUs);
                  videoCompositor.queueInputTexture(
                      inputId, outputTexture, presentationTimeUs, releaseOutputTextureCallback);
                },
                /* textureOutputCapacity= */ 1)
            .setExecutorService(executorService)
            .build();
    return new VideoFrameProcessorTestRunner.Builder()
        .setTestId(testId)
        .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactory)
        .setInputType(INPUT_TYPE_BITMAP)
        .setInputColorInfo(ColorInfo.SRGB_BT709_FULL)
        .setBitmapReader(textureBitmapReader);
  }
}
