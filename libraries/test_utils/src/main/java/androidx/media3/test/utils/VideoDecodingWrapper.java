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
 */

package androidx.media3.test.utils;

import static androidx.media3.common.C.MEDIA_CODEC_PRIORITY_NON_REALTIME;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;

/** A wrapper for decoding a video using {@link MediaCodec}. */
@UnstableApi
@RequiresApi(21)
public final class VideoDecodingWrapper implements AutoCloseable {

  private static final int IMAGE_AVAILABLE_TIMEOUT_MS = 10_000;

  // Use ExoPlayer's 10ms timeout setting. In practise, the test durations from using timeouts of
  // 1/10/100ms don't differ significantly.
  private static final long DEQUEUE_TIMEOUT_US = 10_000;
  // SSIM should be calculated using the luma (Y') channel, thus using the YUV color space.
  private static final int IMAGE_READER_COLOR_SPACE = ImageFormat.YUV_420_888;
  private static final int MEDIA_CODEC_COLOR_SPACE =
      MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
  private static final String ASSET_FILE_SCHEME = "asset:///";

  private final MediaFormat mediaFormat;
  private final MediaCodec mediaCodec;
  private final MediaExtractor mediaExtractor;
  private final MediaCodec.BufferInfo bufferInfo;
  private final ImageReader imageReader;
  private final ConditionVariable imageAvailableConditionVariable;
  private final int comparisonInterval;

  private boolean isCurrentFrameComparisonFrame;
  private boolean hasReadEndOfInputStream;
  private boolean queuedEndOfStreamToDecoder;
  private boolean dequeuedAllDecodedFrames;
  private boolean isCodecStarted;
  private int dequeuedFramesCount;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param filePath The path to the video file.
   * @param comparisonInterval The number of frames between the frames selected for comparison.
   * @param maxImagesAllowed The max number of images allowed in {@link ImageReader}.
   * @throws IOException When failed to open the video file.
   */
  public VideoDecodingWrapper(
      Context context, String filePath, int comparisonInterval, int maxImagesAllowed)
      throws IOException {
    this.comparisonInterval = comparisonInterval;
    mediaExtractor = new MediaExtractor();
    bufferInfo = new MediaCodec.BufferInfo();

    if (filePath.contains(ASSET_FILE_SCHEME)) {
      AssetFileDescriptor assetFd =
          context.getAssets().openFd(filePath.replace(ASSET_FILE_SCHEME, ""));
      mediaExtractor.setDataSource(
          assetFd.getFileDescriptor(), assetFd.getStartOffset(), assetFd.getLength());
    } else {
      mediaExtractor.setDataSource(filePath);
    }

    @Nullable MediaFormat mediaFormat = null;
    for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
      if (MimeTypes.isVideo(mediaExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) {
        mediaFormat = mediaExtractor.getTrackFormat(i);
        mediaExtractor.selectTrack(i);
        break;
      }
    }

    checkStateNotNull(mediaFormat);
    checkState(mediaFormat.containsKey(MediaFormat.KEY_WIDTH));
    int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
    checkState(mediaFormat.containsKey(MediaFormat.KEY_HEIGHT));
    int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

    // Create a handler for the main thread to receive image available notifications. The current
    // (test) thread blocks until this callback is received.
    Handler mainThreadHandler = Util.createHandlerForCurrentOrMainLooper();
    imageAvailableConditionVariable = new ConditionVariable();
    imageReader =
        ImageReader.newInstance(width, height, IMAGE_READER_COLOR_SPACE, maxImagesAllowed);
    imageReader.setOnImageAvailableListener(
        imageReader -> imageAvailableConditionVariable.open(), mainThreadHandler);

    String sampleMimeType = checkNotNull(mediaFormat.getString(MediaFormat.KEY_MIME));
    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MEDIA_CODEC_COLOR_SPACE);
    mediaFormat.setInteger(MediaFormat.KEY_PRIORITY, MEDIA_CODEC_PRIORITY_NON_REALTIME);
    this.mediaFormat = mediaFormat;
    mediaCodec = MediaCodec.createDecoderByType(sampleMimeType);
  }

  /**
   * Returns the next decoded comparison frame, or {@code null} if the stream has ended. The caller
   * takes ownership of any returned image and is responsible for closing it before calling this
   * method again.
   */
  @Nullable
  public Image runUntilComparisonFrameOrEnded() throws InterruptedException {
    if (!isCodecStarted) {
      mediaCodec.configure(
          mediaFormat, imageReader.getSurface(), /* crypto= */ null, /* flags= */ 0);
      mediaCodec.start();
      isCodecStarted = true;
    }
    while (!hasEnded() && !isCurrentFrameComparisonFrame) {
      while (dequeueOneFrameFromDecoder()) {}
      while (queueOneFrameToDecoder()) {}
    }
    if (isCurrentFrameComparisonFrame && !hasEnded()) {
      isCurrentFrameComparisonFrame = false;
      assertThat(imageAvailableConditionVariable.block(IMAGE_AVAILABLE_TIMEOUT_MS)).isTrue();
      imageAvailableConditionVariable.close();
      return imageReader.acquireLatestImage();
    }
    return null;
  }

  /** Returns whether decoding has ended. */
  private boolean hasEnded() {
    return dequeuedAllDecodedFrames;
  }

  /** Returns whether a frame is queued to the {@link MediaCodec decoder}. */
  private boolean queueOneFrameToDecoder() {
    if (queuedEndOfStreamToDecoder) {
      return false;
    }

    int inputBufferIndex = mediaCodec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
    if (inputBufferIndex < 0) {
      return false;
    }

    if (hasReadEndOfInputStream) {
      mediaCodec.queueInputBuffer(
          inputBufferIndex,
          /* offset= */ 0,
          /* size= */ 0,
          /* presentationTimeUs= */ 0,
          MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      queuedEndOfStreamToDecoder = true;
      return false;
    }

    ByteBuffer inputBuffer = checkNotNull(mediaCodec.getInputBuffer(inputBufferIndex));
    int sampleSize = mediaExtractor.readSampleData(inputBuffer, /* offset= */ 0);
    mediaCodec.queueInputBuffer(
        inputBufferIndex,
        /* offset= */ 0,
        sampleSize,
        mediaExtractor.getSampleTime(),
        mediaExtractor.getSampleFlags());
    // MediaExtractor.advance does not reliably return false for end-of-stream, so check sample
    // metadata instead as a more reliable signal. See [internal: b/121204004].
    mediaExtractor.advance();
    hasReadEndOfInputStream = mediaExtractor.getSampleTime() == -1;
    return true;
  }

  /** Returns whether a frame is decoded, renders the frame if the frame is a comparison frame. */
  private boolean dequeueOneFrameFromDecoder() {
    if (isCurrentFrameComparisonFrame) {
      return false;
    }

    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US);
    if (outputBufferIndex < 0) {
      return false;
    }
    isCurrentFrameComparisonFrame = dequeuedFramesCount % comparisonInterval == 0;
    dequeuedFramesCount++;
    mediaCodec.releaseOutputBuffer(outputBufferIndex, /* render= */ isCurrentFrameComparisonFrame);

    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      dequeuedAllDecodedFrames = true;
    }
    return true;
  }

  @Override
  public void close() {
    mediaExtractor.release();
    mediaCodec.release();
    imageReader.close();
  }
}
