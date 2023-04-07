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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromCurrentGlFramebuffer;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createGlTextureFromBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.Matrix;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for texture processing via {@link OverlayShaderProgram}.
 *
 * <p>Expected bitmaps are taken from an emulator, so tests on different emulators or physical
 * devices may fail. To test on other devices, please increase the {@link
 * BitmapPixelTestUtil#MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE} and/or inspect the saved output
 * bitmaps as recommended in {@link DefaultVideoFrameProcessorPixelTest}.
 */
@RunWith(AndroidJUnit4.class)
public class OverlayShaderProgramPixelTest {
  private static final String OVERLAY_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";
  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/original.png";
  private static final String OVERLAY_BITMAP_DEFAULT =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_default.png";
  private static final String OVERLAY_BITMAP_ANCHORED =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_anchored.png";
  private static final String OVERLAY_BITMAP_SCALED =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_scaled.png";
  private static final String OVERLAY_BITMAP_TRANSLUCENT =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_bitmap_translucent.png";
  private static final String OVERLAY_TEXT_DEFAULT =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_text_default.png";
  private static final String OVERLAY_TEXT_TRANSLATE =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_text_translate.png";
  private static final String OVERLAY_MULTIPLE =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_multiple.png";
  private static final String OVERLAY_OVERLAP =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/overlay_overlap.png";

  private final Context context = getApplicationContext();

  private @MonotonicNonNull EGLDisplay eglDisplay;
  private @MonotonicNonNull EGLContext eglContext;
  private @MonotonicNonNull SingleFrameGlShaderProgram overlayShaderProgram;
  private @MonotonicNonNull EGLSurface placeholderEglSurface;
  private int inputTexId;
  private int inputWidth;
  private int inputHeight;

  @Before
  public void createGlObjects() throws IOException, GlUtil.GlException {
    eglDisplay = GlUtil.createEglDisplay();
    eglContext = GlUtil.createEglContext(eglDisplay);
    placeholderEglSurface = GlUtil.focusPlaceholderEglSurface(eglContext, eglDisplay);

    Bitmap inputBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);
    inputWidth = inputBitmap.getWidth();
    inputHeight = inputBitmap.getHeight();
    inputTexId = createGlTextureFromBitmap(inputBitmap);
  }

  @After
  public void release() throws GlUtil.GlException, VideoFrameProcessingException {
    if (overlayShaderProgram != null) {
      overlayShaderProgram.release();
    }
    GlUtil.destroyEglContext(eglDisplay, eglContext);
  }

  @Test
  public void drawFrame_noOverlay_leavesFrameUnchanged() throws Exception {
    String testId = "drawFrame_noOverlay";
    overlayShaderProgram =
        new OverlayEffect(/* textureOverlays= */ ImmutableList.of())
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_bitmapOverlay_blendsBitmapIntoFrame() throws Exception {
    String testId = "drawFrame_bitmapOverlay";
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(bitmapOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_BITMAP_DEFAULT);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_scaledBitmapOverlay_letterboxStretchesOverlay() throws Exception {
    String testId = "drawFrame_scaledBitmapOverlay";
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    float[] scaleMatrix = GlUtil.create4x4IdentityMatrix();
    OverlaySettings overlaySettings = new OverlaySettings.Builder().setMatrix(scaleMatrix).build();
    BitmapOverlay staticBitmapOverlay =
        new BitmapOverlay() {
          @Override
          public Bitmap getBitmap(long presentationTimeUs) {
            return overlayBitmap;
          }

          @Override
          public void configure(Size videoSize) {
            Matrix.scaleM(
                scaleMatrix,
                /* mOffset= */ 0,
                /* x= */ videoSize.getWidth() / (float) overlayBitmap.getWidth(),
                /* y= */ 1,
                /* z= */ 1);
          }

          @Override
          public OverlaySettings getOverlaySettings(long presentationTimeUs) {
            return overlaySettings;
          }
        };
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(staticBitmapOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_BITMAP_SCALED);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_anchoredBitmapOverlay_blendsBitmapIntoTopLeftOfFrame() throws Exception {
    String testId = "drawFrame_anchoredBitmapOverlay";
    Bitmap overlayBitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    float[] translateMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.translateM(translateMatrix, /* mOffset= */ 0, /* x= */ -1f, /* y= */ 1f, /* z= */ 1);
    OverlaySettings overlaySettings =
        new OverlaySettings.Builder().setMatrix(translateMatrix).setAnchor(-1f, 1f).build();
    BitmapOverlay staticBitmapOverlay =
        BitmapOverlay.createStaticBitmapOverlay(overlayBitmap, overlaySettings);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(staticBitmapOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_BITMAP_ANCHORED);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_translucentBitmapOverlay_blendsBitmapIntoFrame() throws Exception {
    String testId = "drawFrame_translucentBitmapOverlay";
    Bitmap bitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    OverlaySettings overlaySettings = new OverlaySettings.Builder().setAlpha(0.5f).build();
    BitmapOverlay translucentBitmapOverlay =
        BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(translucentBitmapOverlay))
            .toGlShaderProgram(context, false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_BITMAP_TRANSLUCENT);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_transparentTextOverlay_blendsBitmapIntoFrame() throws Exception {
    String testId = "drawFrame_transparentTextOverlay";
    SpannableString overlayText = new SpannableString(/* source= */ "Text styling");
    OverlaySettings overlaySettings = new OverlaySettings.Builder().setAlpha(0f).build();
    overlayText.setSpan(
        new ForegroundColorSpan(Color.GRAY),
        /* start= */ 0,
        /* end= */ 4,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    TextOverlay staticTextOverlay =
        TextOverlay.createStaticTextOverlay(overlayText, overlaySettings);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(staticTextOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(ORIGINAL_PNG_ASSET_PATH);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_textOverlay_blendsTextIntoFrame() throws Exception {
    String testId = "drawFrame_textOverlay";
    SpannableString overlayText = new SpannableString(/* source= */ "Text styling");
    overlayText.setSpan(
        new ForegroundColorSpan(Color.GRAY),
        /* start= */ 0,
        /* end= */ 4,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    TextOverlay staticTextOverlay = TextOverlay.createStaticTextOverlay(overlayText);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(staticTextOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_TEXT_DEFAULT);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_translatedTextOverlay_blendsTextIntoFrame() throws Exception {
    String testId = "drawFrame_translatedTextOverlay";
    float[] translateMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.translateM(translateMatrix, /* mOffset= */ 0, /* x= */ 0.5f, /* y= */ 0.5f, /* z= */ 1);
    SpannableString overlayText = new SpannableString(/* source= */ "Text styling");
    overlayText.setSpan(
        new ForegroundColorSpan(Color.GRAY),
        /* start= */ 0,
        /* end= */ 4,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    OverlaySettings overlaySettings =
        new OverlaySettings.Builder().setMatrix(translateMatrix).build();
    TextOverlay staticTextOverlay =
        TextOverlay.createStaticTextOverlay(overlayText, overlaySettings);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(staticTextOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_TEXT_TRANSLATE);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_multipleOverlays_blendsBothIntoFrame() throws Exception {
    String testId = "drawFrame_multipleOverlays";
    float[] translateMatrix1 = GlUtil.create4x4IdentityMatrix();
    Matrix.translateM(translateMatrix1, /* mOffset= */ 0, /* x= */ 0.5f, /* y= */ 0.5f, /* z= */ 1);
    SpannableString overlayText = new SpannableString(/* source= */ "Overlay 1");
    overlayText.setSpan(
        new ForegroundColorSpan(Color.GRAY),
        /* start= */ 0,
        /* end= */ 4,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    OverlaySettings overlaySettings1 =
        new OverlaySettings.Builder().setMatrix(translateMatrix1).build();
    TextOverlay textOverlay = TextOverlay.createStaticTextOverlay(overlayText, overlaySettings1);
    Bitmap bitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    OverlaySettings overlaySettings2 = new OverlaySettings.Builder().setAlpha(0.5f).build();
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings2);
    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(textOverlay, bitmapOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_MULTIPLE);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void drawFrame_overlappingOverlays_blendsOnFifoOrder() throws Exception {
    String testId = "drawFrame_overlappingOverlays";
    SpannableString overlayText = new SpannableString(/* source= */ "Overlapping text");
    overlayText.setSpan(
        new ForegroundColorSpan(Color.WHITE),
        /* start= */ 0,
        /* end= */ overlayText.length(),
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    float[] scaleTextMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.scaleM(scaleTextMatrix, /* mOffset= */ 0, /* x= */ 0.5f, /* y= */ 0.5f, /* z= */ 1);
    OverlaySettings overlaySettings1 =
        new OverlaySettings.Builder().setMatrix(scaleTextMatrix).build();
    TextOverlay textOverlay = TextOverlay.createStaticTextOverlay(overlayText, overlaySettings1);
    Bitmap bitmap = readBitmap(OVERLAY_PNG_ASSET_PATH);
    float[] scaleMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.scaleM(scaleMatrix, /* mOffset= */ 0, /* x= */ 3, /* y= */ 3, /* z= */ 1);
    OverlaySettings overlaySettings2 = new OverlaySettings.Builder().setMatrix(scaleMatrix).build();
    BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings2);

    overlayShaderProgram =
        new OverlayEffect(ImmutableList.of(bitmapOverlay, textOverlay))
            .toGlShaderProgram(context, /* useHdr= */ false);
    Size outputSize = overlayShaderProgram.configure(inputWidth, inputHeight);
    setupOutputTexture(outputSize.getWidth(), outputSize.getHeight());
    Bitmap expectedBitmap = readBitmap(OVERLAY_OVERLAP);

    overlayShaderProgram.drawFrame(inputTexId, /* presentationTimeUs= */ 0);
    Bitmap actualBitmap =
        createArgb8888BitmapFromCurrentGlFramebuffer(outputSize.getWidth(), outputSize.getHeight());

    maybeSaveTestBitmap(testId, /* bitmapLabel= */ "actual", actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setupOutputTexture(int outputWidth, int outputHeight) throws GlUtil.GlException {
    int outputTexId =
        GlUtil.createTexture(
            outputWidth, outputHeight, /* useHighPrecisionColorComponents= */ false);
    int frameBuffer = GlUtil.createFboForTexture(outputTexId);
    GlUtil.focusFramebuffer(
        checkNotNull(eglDisplay),
        checkNotNull(eglContext),
        checkNotNull(placeholderEglSurface),
        frameBuffer,
        outputWidth,
        outputHeight);
  }
}
