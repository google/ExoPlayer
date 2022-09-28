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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static com.google.android.exoplayer2.util.Util.SDK_INT;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A default {@link Codec} implementation that uses {@link MediaCodec}. */
public final class DefaultCodec implements Codec {

  private static final String TAG = "DefaultCodec";

  // MediaCodec decoders always output 16 bit PCM, unless configured to output PCM float.
  // https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers.
  private static final int MEDIA_CODEC_PCM_ENCODING = C.ENCODING_PCM_16BIT;

  private final BufferInfo outputBufferInfo;
  /** The {@link MediaFormat} used to configure the underlying {@link MediaCodec}. */
  private final MediaFormat configurationMediaFormat;

  private final Format configurationFormat;
  private final MediaCodec mediaCodec;
  @Nullable private final Surface inputSurface;
  private final boolean decoderNeedsFrameDroppingWorkaround;

  private @MonotonicNonNull Format outputFormat;
  @Nullable private ByteBuffer outputBuffer;

  private int inputBufferIndex;
  private int outputBufferIndex;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;

  /**
   * Creates a {@code DefaultCodec}.
   *
   * @param context The {@link Context}.
   * @param configurationFormat The {@link Format} to configure the {@code DefaultCodec}. See {@link
   *     #getConfigurationFormat()}. The {@link Format#sampleMimeType sampleMimeType} must not be
   *     {@code null}.
   * @param configurationMediaFormat The {@link MediaFormat} to configure the underlying {@link
   *     MediaCodec}.
   * @param mediaCodecName The name of a specific {@link MediaCodec} to instantiate.
   * @param isDecoder Whether the {@code DefaultCodec} is intended as a decoder.
   * @param outputSurface The output {@link Surface} if the {@link MediaCodec} outputs to a surface.
   */
  public DefaultCodec(
      Context context,
      Format configurationFormat,
      MediaFormat configurationMediaFormat,
      String mediaCodecName,
      boolean isDecoder,
      @Nullable Surface outputSurface)
      throws TransformationException {
    this.configurationFormat = configurationFormat;
    this.configurationMediaFormat = configurationMediaFormat;
    outputBufferInfo = new BufferInfo();
    inputBufferIndex = C.INDEX_UNSET;
    outputBufferIndex = C.INDEX_UNSET;

    boolean isVideo = MimeTypes.isVideo(checkNotNull(configurationFormat.sampleMimeType));
    @Nullable MediaCodec mediaCodec = null;
    @Nullable Surface inputSurface = null;
    try {
      boolean requestedHdrToneMapping =
          SDK_INT >= 29 && Api29.isSdrToneMappingEnabled(configurationMediaFormat);
      mediaCodec = MediaCodec.createByCodecName(mediaCodecName);
      configureCodec(mediaCodec, configurationMediaFormat, isDecoder, outputSurface);
      if (SDK_INT >= 29 && requestedHdrToneMapping) {
        // The MediaCodec input format reflects whether tone-mapping is possible after configure().
        // See
        // https://developer.android.com/reference/android/media/MediaFormat#KEY_COLOR_TRANSFER_REQUEST.
        checkArgument(
            Api29.isSdrToneMappingEnabled(mediaCodec.getInputFormat()),
            "Tone-mapping requested but not supported by the decoder.");
      }
      if (isVideo && !isDecoder) {
        inputSurface = mediaCodec.createInputSurface();
      }
      startCodec(mediaCodec);
    } catch (Exception e) {
      if (inputSurface != null) {
        inputSurface.release();
      }
      if (mediaCodec != null) {
        mediaCodec.release();
      }

      throw createInitializationTransformationException(
          e, configurationMediaFormat, isVideo, isDecoder, mediaCodecName);
    }
    this.mediaCodec = mediaCodec;
    this.inputSurface = inputSurface;
    decoderNeedsFrameDroppingWorkaround = decoderNeedsFrameDroppingWorkaround(context);
  }

  @Override
  public Format getConfigurationFormat() {
    return configurationFormat;
  }

  @Override
  public Surface getInputSurface() {
    return checkStateNotNull(inputSurface);
  }

  @Override
  public int getMaxPendingFrameCount() {
    if (decoderNeedsFrameDroppingWorkaround) {
      // Allow a maximum of one frame to be pending at a time to prevent frame dropping.
      // TODO(b/226330223): Investigate increasing this limit.
      return 1;
    }
    if (Ascii.toUpperCase(getName()).startsWith("OMX.")) {
      // Some OMX decoders don't correctly track their number of output buffers available, and get
      // stuck if too many frames are rendered without being processed, so limit the number of
      // pending frames to avoid getting stuck. This value is experimentally determined. See also
      // b/213455700, b/230097284, b/229978305, and b/245491744.
      // TODO(b/230097284): Add a maximum API check after we know which APIs will never use OMX.
      return 5;
    }
    // Otherwise don't limit the number of frames that can be pending at a time, to maximize
    // throughput.
    return UNLIMITED_PENDING_FRAME_COUNT;
  }

  @Override
  @EnsuresNonNullIf(expression = "#1.data", result = true)
  public boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer)
      throws TransformationException {
    if (inputStreamEnded) {
      return false;
    }
    if (inputBufferIndex < 0) {
      try {
        inputBufferIndex = mediaCodec.dequeueInputBuffer(/* timeoutUs= */ 0);
      } catch (RuntimeException e) {
        throw createTransformationException(e);
      }
      if (inputBufferIndex < 0) {
        return false;
      }
      try {
        inputBuffer.data = mediaCodec.getInputBuffer(inputBufferIndex);
      } catch (RuntimeException e) {
        throw createTransformationException(e);
      }
      inputBuffer.clear();
    }
    checkNotNull(inputBuffer.data);
    return true;
  }

  @Override
  public void queueInputBuffer(DecoderInputBuffer inputBuffer) throws TransformationException {
    checkState(
        !inputStreamEnded, "Input buffer can not be queued after the input stream has ended.");

    int offset = 0;
    int size = 0;
    if (inputBuffer.data != null && inputBuffer.data.hasRemaining()) {
      offset = inputBuffer.data.position();
      size = inputBuffer.data.remaining();
    }
    int flags = 0;
    if (inputBuffer.isEndOfStream()) {
      inputStreamEnded = true;
      flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }
    try {
      mediaCodec.queueInputBuffer(inputBufferIndex, offset, size, inputBuffer.timeUs, flags);
    } catch (RuntimeException e) {
      throw createTransformationException(e);
    }
    inputBufferIndex = C.INDEX_UNSET;
    inputBuffer.data = null;
  }

  @Override
  public void signalEndOfInputStream() throws TransformationException {
    try {
      mediaCodec.signalEndOfInputStream();
    } catch (RuntimeException e) {
      throw createTransformationException(e);
    }
  }

  @Override
  @Nullable
  public Format getOutputFormat() throws TransformationException {
    // The format is updated when dequeueing a 'special' buffer index, so attempt to dequeue now.
    maybeDequeueOutputBuffer(/* setOutputBuffer= */ false);
    return outputFormat;
  }

  @Override
  @Nullable
  public ByteBuffer getOutputBuffer() throws TransformationException {
    return maybeDequeueOutputBuffer(/* setOutputBuffer= */ true) ? outputBuffer : null;
  }

  @Override
  @Nullable
  public BufferInfo getOutputBufferInfo() throws TransformationException {
    return maybeDequeueOutputBuffer(/* setOutputBuffer= */ false) ? outputBufferInfo : null;
  }

  @Override
  public void releaseOutputBuffer(boolean render) throws TransformationException {
    outputBuffer = null;
    try {
      if (render) {
        mediaCodec.releaseOutputBuffer(
            outputBufferIndex,
            /* renderTimestampNs= */ checkStateNotNull(outputBufferInfo).presentationTimeUs * 1000);
      } else {
        mediaCodec.releaseOutputBuffer(outputBufferIndex, /* render= */ false);
      }
    } catch (RuntimeException e) {
      throw createTransformationException(e);
    }
    outputBufferIndex = C.INDEX_UNSET;
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded && outputBufferIndex == C.INDEX_UNSET;
  }

  @Override
  public void release() {
    outputBuffer = null;
    if (inputSurface != null) {
      inputSurface.release();
    }
    mediaCodec.release();
  }

  /**
   * {@inheritDoc}
   *
   * <p>This name is of the actual codec, which may not be the same as the {@code mediaCodecName}
   * passed to {@link #DefaultCodec(Context, Format, MediaFormat, String, boolean, Surface)}.
   *
   * @see MediaCodec#getCanonicalName()
   */
  @Override
  public String getName() {
    return SDK_INT >= 29 ? Api29.getCanonicalName(mediaCodec) : mediaCodec.getName();
  }

  @VisibleForTesting
  /* package */ MediaFormat getConfigurationMediaFormat() {
    return configurationMediaFormat;
  }

  /**
   * Attempts to dequeue an output buffer if there is no output buffer pending. Does nothing
   * otherwise.
   *
   * @param setOutputBuffer Whether to read the bytes of the dequeued output buffer and copy them
   *     into {@link #outputBuffer}.
   * @return Whether there is an output buffer available.
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
  private boolean maybeDequeueOutputBuffer(boolean setOutputBuffer) throws TransformationException {
    if (outputBufferIndex >= 0) {
      return true;
    }
    if (outputStreamEnded) {
      return false;
    }

    try {
      outputBufferIndex = mediaCodec.dequeueOutputBuffer(outputBufferInfo, /* timeoutUs= */ 0);
    } catch (RuntimeException e) {
      throw createTransformationException(e);
    }
    if (outputBufferIndex < 0) {
      if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        outputFormat = convertToFormat(mediaCodec.getOutputFormat());
        boolean isToneMappingEnabled =
            SDK_INT >= 29 && Api29.isSdrToneMappingEnabled(configurationMediaFormat);
        ColorInfo expectedColorInfo =
            isToneMappingEnabled ? ColorInfo.SDR_BT709_LIMITED : configurationFormat.colorInfo;
        if (!areColorTransfersEqual(expectedColorInfo, outputFormat.colorInfo)) {
          // TODO(b/237674316): The container ColorInfo's transfer doesn't match the decoder output
          //   MediaFormat, or we requested tone-mapping but it hasn't been applied. We should
          //   reconfigure downstream components for this case instead.
          Log.w(
              TAG,
              "Codec output color format does not match configured color format. Expected: "
                  + expectedColorInfo
                  + ". Actual: "
                  + outputFormat.colorInfo);
        }
      }
      return false;
    }
    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      outputStreamEnded = true;
      if (outputBufferInfo.size == 0) {
        releaseOutputBuffer(/* render= */ false);
        return false;
      }
    }
    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // Encountered a CSD buffer, skip it.
      releaseOutputBuffer(/* render= */ false);
      return false;
    }

    if (setOutputBuffer) {
      try {
        outputBuffer = checkNotNull(mediaCodec.getOutputBuffer(outputBufferIndex));
      } catch (RuntimeException e) {
        throw createTransformationException(e);
      }
      outputBuffer.position(outputBufferInfo.offset);
      outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
    }
    return true;
  }

  private TransformationException createTransformationException(Exception cause) {
    boolean isDecoder = !mediaCodec.getCodecInfo().isEncoder();
    boolean isVideo = MimeTypes.isVideo(configurationFormat.sampleMimeType);
    return TransformationException.createForCodec(
        cause,
        isVideo,
        isDecoder,
        configurationMediaFormat,
        getName(),
        isDecoder
            ? TransformationException.ERROR_CODE_DECODING_FAILED
            : TransformationException.ERROR_CODE_ENCODING_FAILED);
  }

  private static boolean areColorTransfersEqual(
      @Nullable ColorInfo colorInfo1, @Nullable ColorInfo colorInfo2) {
    @C.ColorTransfer int transfer1 = C.COLOR_TRANSFER_SDR;
    if (colorInfo1 != null && colorInfo1.colorTransfer != Format.NO_VALUE) {
      transfer1 = colorInfo1.colorTransfer;
    }
    @C.ColorTransfer int transfer2 = C.COLOR_TRANSFER_SDR;
    if (colorInfo2 != null && colorInfo2.colorTransfer != Format.NO_VALUE) {
      transfer2 = colorInfo2.colorTransfer;
    }
    return transfer1 == transfer2;
  }

  private static TransformationException createInitializationTransformationException(
      Exception cause,
      MediaFormat mediaFormat,
      boolean isVideo,
      boolean isDecoder,
      @Nullable String mediaCodecName) {
    if (cause instanceof IOException || cause instanceof MediaCodec.CodecException) {
      return TransformationException.createForCodec(
          cause,
          isVideo,
          isDecoder,
          mediaFormat,
          mediaCodecName,
          isDecoder
              ? TransformationException.ERROR_CODE_DECODER_INIT_FAILED
              : TransformationException.ERROR_CODE_ENCODER_INIT_FAILED);
    }
    if (cause instanceof IllegalArgumentException) {
      return TransformationException.createForCodec(
          cause,
          isVideo,
          isDecoder,
          mediaFormat,
          mediaCodecName,
          isDecoder
              ? TransformationException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
              : TransformationException.ERROR_CODE_OUTPUT_FORMAT_UNSUPPORTED);
    }
    return TransformationException.createForUnexpected(cause);
  }

  private static Format convertToFormat(MediaFormat mediaFormat) {
    ImmutableList.Builder<byte[]> csdBuffers = new ImmutableList.Builder<>();
    int csdIndex = 0;
    while (true) {
      @Nullable ByteBuffer csdByteBuffer = mediaFormat.getByteBuffer("csd-" + csdIndex);
      if (csdByteBuffer == null) {
        break;
      }
      byte[] csdBufferData = new byte[csdByteBuffer.remaining()];
      csdByteBuffer.get(csdBufferData);
      csdBuffers.add(csdBufferData);
      csdIndex++;
    }
    String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
    Format.Builder formatBuilder =
        new Format.Builder().setSampleMimeType(mimeType).setInitializationData(csdBuffers.build());
    if (MimeTypes.isVideo(mimeType)) {
      formatBuilder
          .setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
          .setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT))
          .setColorInfo(MediaFormatUtil.getColorInfo(mediaFormat));
    } else if (MimeTypes.isAudio(mimeType)) {
      // TODO(b/178685617): Only set the PCM encoding for audio/raw, once we have a way to
      // simulate more realistic codec input/output formats in tests.
      formatBuilder
          .setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
          .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
          .setPcmEncoding(MEDIA_CODEC_PCM_ENCODING);
    }
    return formatBuilder.build();
  }

  /** Calls and traces {@link MediaCodec#configure(MediaFormat, Surface, MediaCrypto, int)}. */
  private static void configureCodec(
      MediaCodec codec,
      MediaFormat mediaFormat,
      boolean isDecoder,
      @Nullable Surface outputSurface) {
    TraceUtil.beginSection("configureCodec");
    codec.configure(
        mediaFormat,
        outputSurface,
        /* crypto= */ null,
        isDecoder ? 0 : MediaCodec.CONFIGURE_FLAG_ENCODE);
    TraceUtil.endSection();
  }

  /** Calls and traces {@link MediaCodec#start()}. */
  private static void startCodec(MediaCodec codec) {
    TraceUtil.beginSection("startCodec");
    codec.start();
    TraceUtil.endSection();
  }

  private static boolean decoderNeedsFrameDroppingWorkaround(Context context) {
    // Prior to API 29, decoders may drop frames to keep their output surface from growing out of
    // bounds. From API 29, if the app targets API 29 or later, the {@link
    // MediaFormat#KEY_ALLOW_FRAME_DROP} key prevents frame dropping even when the surface is full.
    // Frame dropping is never desired, so a workaround is needed for older API levels.
    return SDK_INT < 29
        || context.getApplicationContext().getApplicationInfo().targetSdkVersion < 29;
  }

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static String getCanonicalName(MediaCodec mediaCodec) {
      return mediaCodec.getCanonicalName();
    }

    @DoNotInline
    public static boolean isSdrToneMappingEnabled(MediaFormat mediaFormat) {
      // MediaFormat.getInteger(String, int) was added in API 29 but applying a color transfer
      // request is only possible from API 31.
      return SDK_INT >= 31
          && mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, /* defaultValue= */ 0)
              == MediaFormat.COLOR_TRANSFER_SDR_VIDEO;
    }
  }
}
