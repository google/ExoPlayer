/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.ffmpeg;

import android.util.Log;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.SimpleDecoder;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoDecoderInputBuffer;
import com.google.android.exoplayer2.video.VideoDecoderOutputBuffer;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Ffmpeg Video decoder.
 */
/* package */ final class FfmpegVideoDecoder
    extends
    SimpleDecoder<VideoDecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegVideoDecoderException> {

  // Error codes matching ffmpeg_video_jni.cc.
  private static final int DECODER_ERROR_INVALID_DATA = -1;
  private static final int DECODER_ERROR_OTHER = -2;
  private static final int DECODER_ERROR_READ_FRAME = -3;
  private static final int DECODER_ERROR_SEND_PACKET = -4;

  private final String codecName;
  private long nativeContext;
  @Nullable private final byte[] extraData;
  private Format format;

  @C.VideoOutputMode private volatile int outputMode;

  /**
   * Creates a Ffmpeg video Decoder.
   *
   * @param numInputBuffers Number of input buffers.
   * @param numOutputBuffers Number of output buffers.
   * @param initialInputBufferSize The initial size of each input buffer, in bytes.
   * @param threads Number of threads libgav1 will use to decode.
   * @throws FfmpegVideoDecoderException Thrown if an exception occurs when initializing the
   * decoder.
   */
  public FfmpegVideoDecoder(
      int numInputBuffers, int numOutputBuffers, int initialInputBufferSize, int threads, Format format)
      throws FfmpegVideoDecoderException {
    super(
        new VideoDecoderInputBuffer[numInputBuffers],
        new VideoDecoderOutputBuffer[numOutputBuffers]);
    if (!FfmpegLibrary.isAvailable()) {
      throw new FfmpegVideoDecoderException("Failed to load decoder native library.");
    }
    codecName = Assertions.checkNotNull(FfmpegLibrary.getVideoCodecName(format.sampleMimeType));
    extraData = getExtraData(format.sampleMimeType, format.initializationData);
    this.format = format;
    nativeContext = ffmpegInitialize(codecName, extraData, threads);
    if (nativeContext == 0) {
      throw new FfmpegVideoDecoderException("Failed to initialize decoder.");
    }
    setInitialInputBufferSize(initialInputBufferSize);
  }

  /**
   * Returns FFmpeg-compatible codec-specific initialization data ("extra data"), or {@code null} if
   * not required.
   */
  @Nullable
  private static byte[] getExtraData(String mimeType, List<byte[]> initializationData) {
    switch (mimeType) {
      case MimeTypes.VIDEO_H264:
        byte[] sps = initializationData.get(0);
        byte[] pps = initializationData.get(1);
        byte[] extraData = new byte[sps.length + pps.length];
        System.arraycopy(sps, 0, extraData, 0, sps.length);
        System.arraycopy(pps, 0, extraData, sps.length, pps.length);
        return extraData;
      case MimeTypes.VIDEO_H265:
        return initializationData.get(0);
      default:
        // Other codecs do not require extra data.
        return null;
    }
  }

  @Override
  public String getName() {
    return "ffmpeg" + FfmpegLibrary.getVersion() + "-" + codecName;
  }

  /**
   * Sets the output mode for frames rendered by the decoder.
   *
   * @param outputMode The output mode.
   */
  public void setOutputMode(@C.VideoOutputMode int outputMode) {
    this.outputMode = outputMode;
  }

  @Override
  protected VideoDecoderInputBuffer createInputBuffer() {
    return new VideoDecoderInputBuffer();
  }

  @Override
  protected VideoDecoderOutputBuffer createOutputBuffer() {
    return new VideoDecoderOutputBuffer(this::releaseOutputBuffer);
  }

  @Override
  @Nullable
  protected FfmpegVideoDecoderException decode(
      VideoDecoderInputBuffer inputBuffer, VideoDecoderOutputBuffer outputBuffer, boolean reset) {
    if (reset) {
      nativeContext = ffmpegReset(nativeContext);
      if (nativeContext == 0) {
        return new FfmpegVideoDecoderException("Error resetting (see logcat).");
      }
    }

    // send packet
    ByteBuffer inputData = Util.castNonNull(inputBuffer.data);
    int inputSize = inputData.limit();
    // enqueue origin data
    boolean needSendAgain = false;
    int sendPacketResult = ffmpegSendPacket(nativeContext, inputData, inputSize,
        inputBuffer.timeUs);
    if (sendPacketResult == DECODER_ERROR_INVALID_DATA) {
      outputBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);
      return null;
    } else if (sendPacketResult == DECODER_ERROR_READ_FRAME) {
      // need read frame
      needSendAgain = true;
    } else if (sendPacketResult == DECODER_ERROR_OTHER) {
      return new FfmpegVideoDecoderException("ffmpegDecode error: (see logcat)");
    }

    // receive frame
    boolean decodeOnly = inputBuffer.isDecodeOnly();
    // We need to dequeue the decoded frame from the decoder even when the input data is
    // decode-only.
    int getFrameResult = ffmpegReceiveFrame(nativeContext, outputMode, outputBuffer, decodeOnly);
    if (getFrameResult == DECODER_ERROR_SEND_PACKET) {
      return null;
    } else if (getFrameResult == DECODER_ERROR_OTHER) {
      return new FfmpegVideoDecoderException("ffmpegDecode error: (see logcat)");
    }

    if (getFrameResult == DECODER_ERROR_INVALID_DATA) {
      outputBuffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }

    if (!decodeOnly) {
      outputBuffer.colorInfo = inputBuffer.colorInfo;
    }

    if (needSendAgain) {
      Log.e("ffmpeg_jni", "timeUs=" + inputBuffer.timeUs + ", " + "nendSendAagin");
    }

    return null;
  }

  @Override
  protected FfmpegVideoDecoderException createUnexpectedDecodeException(Throwable error) {
    return new FfmpegVideoDecoderException("Unexpected decode error", error);
  }

  @Override
  public void release() {
    super.release();
    ffmpegRelease(nativeContext);
    nativeContext = 0;
  }

  @Override
  protected void releaseOutputBuffer(VideoDecoderOutputBuffer buffer) {
    // Decode only frames do not acquire a reference on the internal decoder buffer and thus do not
    // require a call to vpxReleaseFrame.
//    if (outputMode == C.VIDEO_OUTPUT_MODE_SURFACE_YUV && !buffer.isDecodeOnly()) {
//      gav1ReleaseFrame(nativeContext, buffer);
//    }
    super.releaseOutputBuffer(buffer);
  }

  /**
   * Renders output buffer to the given surface. Must only be called when in {@link
   * C#VIDEO_OUTPUT_MODE_SURFACE_YUV} mode.
   *
   * @param outputBuffer Output buffer.
   * @param surface Output surface.
   * @throws FfmpegVideoDecoderException Thrown if called with invalid output mode or frame
   * rendering fails.
   */
  public void renderToSurface(VideoDecoderOutputBuffer outputBuffer, Surface surface)
      throws FfmpegVideoDecoderException {
    if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
      throw new FfmpegVideoDecoderException("Invalid output mode.");
    }
    if (ffmpegRenderFrame(
        nativeContext, surface,
        outputBuffer, outputBuffer.width, outputBuffer.height) == DECODER_ERROR_OTHER) {
      throw new FfmpegVideoDecoderException(
          "Buffer render error: ");
    }
  }

  private native long ffmpegInitialize(String codecName, @Nullable byte[] extraData, int threads);

  private native long ffmpegReset(long context);

  private native void ffmpegRelease(long context);

  private native int ffmpegRenderFrame(
      long context, Surface surface, VideoDecoderOutputBuffer outputBuffer,
      int displayedWidth,
      int displayedHeight);

  /**
   * Decodes the encoded data passed.
   *
   * @param context Decoder context.
   * @param encodedData Encoded data.
   * @param length Length of the data buffer.
   * @return 0 if successful, {@link #DECODER_ERROR_OTHER} if an error occurred.
   */
  private native int ffmpegSendPacket(long context, ByteBuffer encodedData, int length,
      long inputTime);

  /**
   * Gets the decoded frame.
   *
   * @param context Decoder context.
   * @param outputBuffer Output buffer for the decoded frame.
   * @return 0 if successful, {@link #DECODER_ERROR_INVALID_DATA} if successful but the frame is
   * decode-only, {@link #DECODER_ERROR_OTHER} if an error occurred.
   */
  private native int ffmpegReceiveFrame(
      long context, int outputMode, VideoDecoderOutputBuffer outputBuffer, boolean decodeOnly);

}
