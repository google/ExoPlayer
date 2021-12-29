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
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for frame processing via {@link FrameEditor#processData()}. */
@RunWith(AndroidJUnit4.class)
public final class FrameEditorDataProcessingTest {

  private static final String INPUT_MP4_ASSET_STRING = "media/mp4/sample.mp4";
  private static final String NO_EDITS_EXPECTED_OUTPUT_PNG_ASSET_STRING =
      "media/bitmap/sample_mp4_first_frame.png";
  /**
   * Maximum allowed average pixel difference between the expected and actual edited images for the
   * test to pass. The value is chosen so that differences in decoder behavior across emulator
   * versions shouldn't affect whether the test passes, but substantial distortions introduced by
   * changes in the behavior of the frame editor will cause the test to fail.
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

  @Before
  public void setUp() throws Exception {
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
      Matrix identityMatrix = new Matrix();
      frameEditor =
          FrameEditor.create(
              getApplicationContext(),
              width,
              height,
              PIXEL_WIDTH_HEIGHT_RATIO,
              identityMatrix,
              frameEditorOutputImageReader.getSurface(),
              Transformer.DebugViewProvider.NONE);

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
      assertThat(frameEditor.hasInputData()).isTrue();
    } finally {
      mediaExtractor.release();
      if (mediaCodec != null) {
        mediaCodec.release();
      }
    }
  }

  @After
  public void tearDown() {
    if (frameEditor != null) {
      frameEditor.release();
    }
  }

  @Test
  public void processData_noEdits_producesExpectedOutput() throws Exception {
    Bitmap expectedBitmap;
    try (InputStream inputStream =
        getApplicationContext().getAssets().open(NO_EDITS_EXPECTED_OUTPUT_PNG_ASSET_STRING)) {
      expectedBitmap = BitmapFactory.decodeStream(inputStream);
    }

    checkNotNull(frameEditor).processData();
    Image editedImage = checkNotNull(frameEditorOutputImageReader).acquireLatestImage();
    Bitmap editedBitmap = getArgb8888BitmapForRgba8888Image(editedImage);

    // TODO(internal b/207848601): switch to using proper tooling for testing against golden data.
    float averagePixelAbsoluteDifference =
        getAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, editedBitmap);
    assertThat(averagePixelAbsoluteDifference).isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
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
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int color = actual.getPixel(x, y);
        int expectedColor = expected.getPixel(x, y);
        int maximumAbsoluteDifference = 0;
        maximumAbsoluteDifference =
            max(
                maximumAbsoluteDifference,
                abs(((color >> 24) & 0xFF) - ((expectedColor >> 24) & 0xFF)));
        maximumAbsoluteDifference =
            max(
                maximumAbsoluteDifference,
                abs(((color >> 16) & 0xFF) - ((expectedColor >> 16) & 0xFF)));
        maximumAbsoluteDifference =
            max(
                maximumAbsoluteDifference,
                abs(((color >> 8) & 0xFF) - ((expectedColor >> 8) & 0xFF)));
        maximumAbsoluteDifference =
            max(maximumAbsoluteDifference, abs((color & 0xFF) - (expectedColor & 0xFF)));
        sumMaximumAbsoluteDifferences += maximumAbsoluteDifference;
      }
    }
    return (float) sumMaximumAbsoluteDifferences / (width * height);
  }
}
