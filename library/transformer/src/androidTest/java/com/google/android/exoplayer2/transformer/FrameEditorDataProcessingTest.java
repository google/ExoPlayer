/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.abs;
import static java.lang.Math.max;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pixel test for frame processing via {@link FrameEditor#processData()}. Expected images are taken
 * from emulators, so tests on physical devices may fail. To test on physical devices, please modify
 * the MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE.
 */
// TODO(b/214510265): Fix these tests on Pixel 4 emulators.
@RunWith(AndroidJUnit4.class)
public final class FrameEditorDataProcessingTest {

  // Input MP4 file to transform.
  private static final String INPUT_MP4_ASSET_STRING = "media/mp4/sample.mp4";

  /* Expected first frames after transformation.
   * To generate new "expected" assets:
   * 1. Insert this code into a test, to download some editedBitmap.
   *  + try (FileOutputStream fileOutputStream = new FileOutputStream("/sdcard/tmp.png")) {
   *  +    // quality is ignored
   *  +   editedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
   *  + }
   * 2. Run the test on a "Nexus 6P API 23" emulator. Emulators are preferred as the automated
   *    presubmit that will run this test will also be an emulator. API versions 29+ have storage
   *    restrictions that complicate file generation.
   * 3. Open the "Device File Explorer", find "/sdcard/tmp.png", and "Save As..." the file.
   */
  private static final String NO_EDITS_EXPECTED_OUTPUT_PNG_ASSET_STRING =
      "media/bitmap/sample_mp4_first_frame.png";
  private static final String TRANSLATE_RIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING =
      "media/bitmap/sample_mp4_first_frame_translate_right.png";
  private static final String SCALE_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING =
      "media/bitmap/sample_mp4_first_frame_scale_narrow.png";
  private static final String ROTATE_90_EXPECTED_OUTPUT_PNG_ASSET_STRING =
      "media/bitmap/sample_mp4_first_frame_rotate90.png";

  /**
   * Maximum allowed average pixel difference between the expected and actual edited images for the
   * test to pass. The value is chosen so that differences in decoder behavior across emulator
   * versions shouldn't affect whether the test passes, but substantial distortions introduced by
   * changes in the behavior of the frame editor will cause the test to fail.
   *
   * <p>To run this test on physical devices, please use a value of 5f, rather than 0.1f. This
   * higher value will ignore some very small errors, but will allow for some differences caused by
   * graphics implementations to be ignored. When the difference is close to the threshold, manually
   * inspect expected/actual bitmaps to confirm failure, as it's possible this is caused by a
   * difference in the codec or graphics implementation as opposed to a FrameEditor issue.
   */
  private static final float MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE = 0.1f;
  /** Timeout for dequeueing buffers from the codec, in microseconds. */
  private static final int DEQUEUE_TIMEOUT_US = 5_000_000;
  /** Time to wait for the frame editor's input to be populated by the decoder, in milliseconds. */
  private static final int SURFACE_WAIT_MS = 1000;
  /** The ratio of width over height, for each pixel in a frame. */
  private static final float PIXEL_WIDTH_HEIGHT_RATIO = 1;

  private @MonotonicNonNull FrameEditor frameEditor;
  private @MonotonicNonNull ImageReader frameEditorOutputImageReader;
  private @MonotonicNonNull MediaFormat mediaFormat;

  @After
  public void release() {
    if (frameEditor != null) {
      frameEditor.release();
    }
  }

  @Test
  public void processData_noEdits_producesExpectedOutput() throws Exception {
    Matrix identityMatrix = new Matrix();
    setUpAndPrepareFirstFrame(identityMatrix);

    Bitmap expectedBitmap = getBitmap(NO_EDITS_EXPECTED_OUTPUT_PNG_ASSET_STRING);
    checkNotNull(frameEditor).processData();
    Image editedImage = checkNotNull(frameEditorOutputImageReader).acquireLatestImage();
    Bitmap editedBitmap = getArgb8888BitmapForRgba8888Image(editedImage);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, editedBitmap);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_translateRight_producesExpectedOutput() throws Exception {
    Matrix translateRightMatrix = new Matrix();
    translateRightMatrix.postTranslate(/* dx= */ 1, /* dy= */ 0);
    setUpAndPrepareFirstFrame(translateRightMatrix);

    Bitmap expectedBitmap = getBitmap(TRANSLATE_RIGHT_EXPECTED_OUTPUT_PNG_ASSET_STRING);
    checkNotNull(frameEditor).processData();
    Image editedImage = checkNotNull(frameEditorOutputImageReader).acquireLatestImage();
    Bitmap editedBitmap = getArgb8888BitmapForRgba8888Image(editedImage);

    // TODO(b/207848601): switch to using proper tooling for testing against golden
    // data.simple
    float averagePixelAbsoluteDifference =
        getAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, editedBitmap);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_scaleNarrow_producesExpectedOutput() throws Exception {
    Matrix scaleNarrowMatrix = new Matrix();
    scaleNarrowMatrix.postScale(.5f, 1.2f);
    setUpAndPrepareFirstFrame(scaleNarrowMatrix);
    Bitmap expectedBitmap = getBitmap(SCALE_NARROW_EXPECTED_OUTPUT_PNG_ASSET_STRING);

    checkNotNull(frameEditor).processData();
    Image editedImage = checkNotNull(frameEditorOutputImageReader).acquireLatestImage();
    Bitmap editedBitmap = getArgb8888BitmapForRgba8888Image(editedImage);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, editedBitmap);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  @Test
  public void processData_rotate90_producesExpectedOutput() throws Exception {
    // TODO(b/213190310): After creating a Presentation class, move VideoSamplePipeline
    //  resolution-based adjustments (ex. in cl/419619743) to that Presentation class, so we can
    //  test that rotation doesn't distort the image.
    Matrix rotate90Matrix = new Matrix();
    rotate90Matrix.postRotate(/* degrees= */ 90);
    setUpAndPrepareFirstFrame(rotate90Matrix);

    Bitmap expectedBitmap = getBitmap(ROTATE_90_EXPECTED_OUTPUT_PNG_ASSET_STRING);
    checkNotNull(frameEditor).processData();
    Image editedImage = checkNotNull(frameEditorOutputImageReader).acquireLatestImage();
    Bitmap editedBitmap = getArgb8888BitmapForRgba8888Image(editedImage);

    // TODO(b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, editedBitmap);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
  }

  private void setUpAndPrepareFirstFrame(Matrix transformationMatrix) throws Exception {
    // Set up the extractor to read the first video frame and get its format.
    MediaExtractor mediaExtractor = new MediaExtractor();
    @Nullable MediaCodec mediaCodec = null;
    try (AssetFileDescriptor afd =
        getApplicationContext().getAssets().openFd(INPUT_MP4_ASSET_STRING)) {
      mediaExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
        if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
          mediaFormat = mediaExtractor.getTrackFormat(i);
          mediaExtractor.selectTrack(i);
          break;
        }
      }

      int width = checkNotNull(mediaFormat).getInteger(MediaFormat.KEY_WIDTH);
      int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
      frameEditorOutputImageReader =
          ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, /* maxImages= */ 1);
      frameEditor =
          FrameEditor.create(
              getApplicationContext(),
              width,
              height,
              PIXEL_WIDTH_HEIGHT_RATIO,
              transformationMatrix,
              frameEditorOutputImageReader.getSurface(),
              /* enableExperimentalHdrEditing= */ false,
              Transformer.DebugViewProvider.NONE);
      frameEditor.registerInputFrame();

      // Queue the first video frame from the extractor.
      String mimeType = checkNotNull(mediaFormat.getString(MediaFormat.KEY_MIME));
      mediaCodec = MediaCodec.createDecoderByType(mimeType);
      mediaCodec.configure(
          mediaFormat, frameEditor.getInputSurface(), /* crypto= */ null, /* flags= */ 0);
      mediaCodec.start();
      int inputBufferIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
      assertThat(inputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      ByteBuffer inputBuffer = checkNotNull(mediaCodec.getInputBuffers()[inputBufferIndex]);
      int sampleSize = mediaExtractor.readSampleData(inputBuffer, /* offset= */ 0);
      mediaCodec.queueInputBuffer(
          inputBufferIndex,
          /* offset= */ 0,
          sampleSize,
          mediaExtractor.getSampleTime(),
          mediaExtractor.getSampleFlags());

      // Queue an end-of-stream buffer to force the codec to produce output.
      inputBufferIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
      assertThat(inputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      mediaCodec.queueInputBuffer(
          inputBufferIndex,
          /* offset= */ 0,
          /* size= */ 0,
          /* presentationTimeUs= */ 0,
          MediaCodec.BUFFER_FLAG_END_OF_STREAM);

      // Dequeue and render the output video frame.
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      int outputBufferIndex;
      do {
        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
        assertThat(outputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      } while (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
          || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
      mediaCodec.releaseOutputBuffer(outputBufferIndex, /* render= */ true);

      // Sleep to give time for the surface texture to be populated.
      Thread.sleep(SURFACE_WAIT_MS);
      assertThat(frameEditor.canProcessData()).isTrue();
    } finally {
      mediaExtractor.release();
      if (mediaCodec != null) {
        mediaCodec.release();
      }
    }
  }

  private Bitmap getBitmap(String expectedAssetString) throws IOException {
    Bitmap bitmap;
    try (InputStream inputStream = getApplicationContext().getAssets().open(expectedAssetString)) {
      bitmap = BitmapFactory.decodeStream(inputStream);
    }
    return bitmap;
  }

  /**
   * Returns a bitmap with the same information as the provided alpha/red/green/blue 8-bits per
   * component image.
   */
  private static Bitmap getArgb8888BitmapForRgba8888Image(Image image) {
    int width = image.getWidth();
    int height = image.getHeight();
    assertThat(image.getPlanes()).hasLength(1);
    assertThat(image.getFormat()).isEqualTo(PixelFormat.RGBA_8888);
    Image.Plane plane = image.getPlanes()[0];
    ByteBuffer buffer = plane.getBuffer();
    int[] colors = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int offset = y * plane.getRowStride() + x * plane.getPixelStride();
        int r = buffer.get(offset) & 0xFF;
        int g = buffer.get(offset + 1) & 0xFF;
        int b = buffer.get(offset + 2) & 0xFF;
        int a = buffer.get(offset + 3) & 0xFF;
        colors[y * width + x] = (a << 24) + (r << 16) + (g << 8) + b;
      }
    }
    return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
  }

  /**
   * Returns the sum of the absolute differences between the expected and actual bitmaps, calculated
   * using the maximum difference across all color channels for each pixel, then divided by the
   * total number of pixels in the image. The bitmap resolutions must match and they must use
   * configuration {@link Bitmap.Config#ARGB_8888}.
   */
  private static float getAveragePixelAbsoluteDifferenceArgb8888(Bitmap expected, Bitmap actual) {
    int width = actual.getWidth();
    int height = actual.getHeight();
    assertThat(width).isEqualTo(expected.getWidth());
    assertThat(height).isEqualTo(expected.getHeight());
    assertThat(actual.getConfig()).isEqualTo(Bitmap.Config.ARGB_8888);
    long sumMaximumAbsoluteDifferences = 0;
    // Debug-only image diff without alpha. To use, set a breakpoint right before the method return
    // to view the difference between the expected and actual bitmaps. A passing test should show
    // an image that is completely black (color == 0).
    Bitmap debugDiff = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int actualColor = actual.getPixel(x, y);
        int expectedColor = expected.getPixel(x, y);

        int alphaDifference = abs(Color.alpha(actualColor) - Color.alpha(expectedColor));
        int redDifference = abs(Color.red(actualColor) - Color.red(expectedColor));
        int blueDifference = abs(Color.blue(actualColor) - Color.blue(expectedColor));
        int greenDifference = abs(Color.green(actualColor) - Color.green(expectedColor));
        debugDiff.setPixel(x, y, Color.rgb(redDifference, blueDifference, greenDifference));

        int maximumAbsoluteDifference = 0;
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, alphaDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, redDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, blueDifference);
        maximumAbsoluteDifference = max(maximumAbsoluteDifference, greenDifference);

        sumMaximumAbsoluteDifferences += maximumAbsoluteDifference;
      }
    }
    return (float) sumMaximumAbsoluteDifferences / (width * height);
  }
}
