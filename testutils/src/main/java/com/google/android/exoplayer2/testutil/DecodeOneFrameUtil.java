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

package com.google.android.exoplayer2.testutil;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utilities for decoding a frame for tests. */
public class DecodeOneFrameUtil {

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
   * Reads and decodes one frame from the {@code cacheFilePath} and renders it to the {@code
   * surface}.
   *
   * @param cacheFilePath The path to the file in the cache directory.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to, {@code null} if the decoded
   *     frame is not needed.
   */
  public static void decodeOneCacheFileFrame(
      String cacheFilePath, Listener listener, @Nullable Surface surface) throws Exception {
    MediaExtractor mediaExtractor = new MediaExtractor();
    try {
      mediaExtractor.setDataSource(cacheFilePath);
      decodeOneFrame(mediaExtractor, listener, surface);
    } finally {
      mediaExtractor.release();
    }
  }

  /**
   * Reads and decodes one frame from the {@code assetFilePath} and renders it to the {@code
   * surface}.
   *
   * @param assetFilePath The path to the file in the asset directory.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to, {@code null} if the decoded
   *     frame is not needed.
   */
  public static void decodeOneAssetFileFrame(
      String assetFilePath, Listener listener, @Nullable Surface surface) throws Exception {
    MediaExtractor mediaExtractor = new MediaExtractor();
    Context context = getApplicationContext();
    try (AssetFileDescriptor afd = context.getAssets().openFd(assetFilePath)) {
      mediaExtractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
      decodeOneFrame(mediaExtractor, listener, surface);
    } finally {
      mediaExtractor.release();
    }
  }

  /**
   * Reads and decodes one frame from the {@code mediaExtractor} and renders it to the {@code
   * surface}.
   *
   * @param mediaExtractor The {@link MediaExtractor} with a {@link
   *     MediaExtractor#setDataSource(String) data source set}.
   * @param listener A {@link Listener} implementation.
   * @param surface The {@link Surface} to render the decoded frame to, {@code null} if the decoded
   *     frame is not needed.
   */
  private static void decodeOneFrame(
      MediaExtractor mediaExtractor, Listener listener, @Nullable Surface surface)
      throws Exception {
    // Set up the extractor to read the first video frame and get its format.
    if (surface == null) {
      // Creates a placeholder surface.
      surface = new Surface(new SurfaceTexture(/* texName= */ 0));
    }

    @Nullable MediaCodec mediaCodec = null;
    @Nullable MediaFormat mediaFormat = null;

    try {
      for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
        if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
          mediaFormat = mediaExtractor.getTrackFormat(i);
          listener.onContainerExtracted(checkNotNull(mediaFormat));
          mediaExtractor.selectTrack(i);
          break;
        }
      }

      checkStateNotNull(mediaFormat);
      // Queue the first video frame from the extractor.
      String mimeType = checkNotNull(mediaFormat.getString(MediaFormat.KEY_MIME));
      mediaCodec = MediaCodec.createDecoderByType(mimeType);
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

  private DecodeOneFrameUtil() {}
}
