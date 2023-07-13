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

package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.lang.Math.round;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Utilities for decoding a video frame for tests. */
@UnstableApi
public final class DecodeOneFrameUtil {
  public static final String NO_DECODER_SUPPORT_ERROR_STRING =
      "No MediaCodec decoders on this device support this value.";

  /** Listener for decoding events. */
  public interface Listener {
    /** Called when the video {@link MediaFormat} is extracted from the container. */
    void onContainerExtracted(MediaFormat mediaFormat);

    /**
     * Called when the video {@link MediaFormat} is read by the decoder from the byte stream, after
     * a frame is decoded.
     */
    void onFrameDecoded(MediaFormat mediaFormat);
  }

  /** Timeout for dequeueing buffers from the codec, in microseconds. */
  private static final int DEQUEUE_TIMEOUT_US = 5_000_000;

  /**
   * Reads and decodes one frame from the {@code assetFilePath} and renders it to the {@code
   * surface}.
   *
   * @param assetFilePath The path to the file in the asset directory.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to, {@code null} if the decoded
   *     frame is not needed.
   * @throws IOException If the {@link MediaExtractor} or {@link MediaCodec} cannot be created.
   */
  public static void decodeOneAssetFileFrame(
      String assetFilePath, Listener listener, @Nullable Surface surface) throws IOException {
    MediaExtractor mediaExtractor = new MediaExtractor();
    Context context = getApplicationContext();

    try (AssetFileDescriptor afd = context.getAssets().openFd(assetFilePath)) {
      mediaExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      if (surface == null) {
        decodeOneVideoFrame(mediaExtractor, listener);
      } else {
        decodeOneVideoFrame(mediaExtractor, listener, surface);
      }
    } finally {
      mediaExtractor.release();
    }
  }

  /**
   * Reads and decodes one video frame from the {@code mediaExtractor} and renders it to the {@code
   * surface}.
   *
   * <p>A placeholder surface is used.
   *
   * @param mediaExtractor The {@link MediaExtractor} with a {@link
   *     MediaExtractor#setDataSource(String) data source set}.
   * @param listener A {@link Listener} implementation.
   * @throws UnsupportedOperationException If there is no supported {@linkplain MediaCodec decoders}
   *     available.
   * @throws IOException If the {@link MediaExtractor} or {@link MediaCodec} cannot be created.
   */
  private static void decodeOneVideoFrame(MediaExtractor mediaExtractor, Listener listener)
      throws IOException {
    @Nullable SurfaceTexture placeholderSurfaceTexture = null;
    @Nullable Surface placeholderSurface = null;
    try {
      placeholderSurfaceTexture = new SurfaceTexture(/* texName= */ 0);
      placeholderSurface = new Surface(placeholderSurfaceTexture);
      decodeOneVideoFrame(mediaExtractor, listener, placeholderSurface);
    } finally {
      if (placeholderSurfaceTexture != null) {
        placeholderSurfaceTexture.release();
      }
      if (placeholderSurface != null) {
        placeholderSurface.release();
      }
    }
  }

  /**
   * Reads and decodes one video frame from the {@code mediaExtractor} and renders it to the {@code
   * surface}.
   *
   * @param mediaExtractor The {@link MediaExtractor} with a {@link
   *     MediaExtractor#setDataSource(String) data source set}.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to.
   * @throws IOException If the {@link MediaCodec} cannot be created.
   * @throws UnsupportedOperationException If there is no supported {@linkplain MediaCodec decoders}
   *     available.
   */
  private static void decodeOneVideoFrame(
      MediaExtractor mediaExtractor, Listener listener, Surface surface) throws IOException {
    @Nullable MediaFormat mediaFormat = null;
    @Nullable MediaCodec mediaCodec = null;

    try {
      for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
        if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
          mediaFormat = mediaExtractor.getTrackFormat(i);
          listener.onContainerExtracted(checkNotNull(mediaFormat));
          mediaExtractor.selectTrack(i);
          break;
        }
      }

      @Nullable String decoderName = getSupportedDecoderName(checkStateNotNull(mediaFormat));
      if (decoderName == null) {
        throw new UnsupportedOperationException(NO_DECODER_SUPPORT_ERROR_STRING);
      }
      mediaCodec = MediaCodec.createByCodecName(decoderName);

      // Queue the first video frame from the extractor.
      mediaCodec.configure(mediaFormat, surface, /* crypto= */ null, /* flags= */ 0);
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
      boolean decoderFormatRead = false;
      do {
        outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
        if (!decoderFormatRead && outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          listener.onFrameDecoded(mediaCodec.getOutputFormat());
          decoderFormatRead = true;
        }
        assertThat(outputBufferIndex).isNotEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
      } while (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
          || outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
      mediaCodec.releaseOutputBuffer(outputBufferIndex, /* render= */ true);
    } finally {
      if (mediaCodec != null) {
        mediaCodec.release();
      }
    }
  }

  /**
   * Returns the name of a decoder that supports this {@link MediaFormat}.
   *
   * <p>Capability check is similar to
   * androidx.media3.transformer.EncoderUtil.java#findCodecForFormat().
   */
  @Nullable
  private static String getSupportedDecoderName(MediaFormat format) {
    if (Util.SDK_INT < 21) {
      throw new UnsupportedOperationException("Unable to detect decoder support under API 21.");
    }
    // TODO(b/266923205): De-duplicate logic from EncoderUtil.java#findCodecForFormat().
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    // Format must not include KEY_FRAME_RATE on API21.
    // https://developer.android.com/reference/android/media/MediaCodecList#findDecoderForFormat(android.media.MediaFormat)
    float frameRate = Format.NO_VALUE;
    if (Util.SDK_INT == 21 && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
      try {
        frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE);
      } catch (ClassCastException e) {
        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
      }
      // Clears the frame rate field.
      format.setString(MediaFormat.KEY_FRAME_RATE, null);
    }

    @Nullable String mediaCodecName = mediaCodecList.findDecoderForFormat(format);

    if (Util.SDK_INT == 21) {
      MediaFormatUtil.maybeSetInteger(format, MediaFormat.KEY_FRAME_RATE, round(frameRate));
    }
    return mediaCodecName;
  }

  private DecodeOneFrameUtil() {}
}
