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
import com.google.android.exoplayer2.effect.DebugTraceUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MediaFormatUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A default {@link Codec} implementation that uses {@link MediaCodec}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DefaultCodec implements Codec {
  // MediaCodec decoders output 16 bit PCM, unless configured to output PCM float.
  // https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers.
  public static final int DEFAULT_PCM_ENCODING = C.ENCODING_PCM_16BIT;

  private static final String TAG = "DefaultCodec";

  private final BufferInfo outputBufferInfo;
  /** The {@link MediaFormat} used to configure the underlying {@link MediaCodec}. */
  private final MediaFormat configurationMediaFormat;

  private final Format configurationFormat;
  private final MediaCodec mediaCodec;
  @Nullable private final Surface inputSurface;
  private final int maxPendingFrameCount;
  private final boolean isDecoder;
  private final boolean isVideo;

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
      throws ExportException {
    this.configurationFormat = configurationFormat;
    this.configurationMediaFormat = configurationMediaFormat;
    this.isDecoder = isDecoder;
    isVideo = MimeTypes.isVideo(checkNotNull(configurationFormat.sampleMimeType));
    outputBufferInfo = new BufferInfo();
    inputBufferIndex = C.INDEX_UNSET;
    outputBufferIndex = C.INDEX_UNSET;

    @Nullable MediaCodec mediaCodec = null;
    @Nullable Surface inputSurface = null;
    boolean requestedHdrToneMapping = isSdrToneMappingEnabled(configurationMediaFormat);

    try {
      mediaCodec = MediaCodec.createByCodecName(mediaCodecName);
      configureCodec(mediaCodec, configurationMediaFormat, isDecoder, outputSurface);
      if (requestedHdrToneMapping) {
        // The MediaCodec input format reflects whether tone-mapping is possible after configure().
        // See
        // https://developer.android.com/reference/android/media/MediaFormat#KEY_COLOR_TRANSFER_REQUEST.
        checkArgument(
            isSdrToneMappingEnabled(mediaCodec.getInputFormat()),
            "Tone-mapping requested but not supported by the decoder.");
      }
      if (isVideo && !isDecoder) {
        inputSurface = mediaCodec.createInputSurface();
      }
      startCodec(mediaCodec);
    } catch (Exception e) {
      Log.d(TAG, "MediaCodec error", e);

      if (inputSurface != null) {
        inputSurface.release();
      }
      if (mediaCodec != null) {
        mediaCodec.release();
      }

      @ExportException.ErrorCode int errorCode;
      if (e instanceof IOException || e instanceof MediaCodec.CodecException) {
        errorCode =
            isDecoder
                ? ExportException.ERROR_CODE_DECODER_INIT_FAILED
                : ExportException.ERROR_CODE_ENCODER_INIT_FAILED;
      } else if (e instanceof IllegalArgumentException) {
        errorCode =
            isDecoder
                ? ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                : ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED;
      } else {
        errorCode = ExportException.ERROR_CODE_FAILED_RUNTIME_CHECK;
      }
      throw createExportException(e, errorCode, mediaCodecName);
    }
    this.mediaCodec = mediaCodec;
    this.inputSurface = inputSurface;
    maxPendingFrameCount =
        Util.getMaxPendingFramesCountForMediaCodecDecoders(
            context, mediaCodecName, requestedHdrToneMapping);
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
    return maxPendingFrameCount;
  }

  @Override
  @EnsuresNonNullIf(expression = "#1.data", result = true)
  public boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer) throws ExportException {
    if (inputStreamEnded) {
      return false;
    }
    if (inputBufferIndex < 0) {
      try {
        inputBufferIndex = mediaCodec.dequeueInputBuffer(/* timeoutUs= */ 0);
      } catch (RuntimeException e) {
        Log.d(TAG, "MediaCodec error", e);
        throw createExportException(e);
      }
      if (inputBufferIndex < 0) {
        return false;
      }
      try {
        inputBuffer.data = mediaCodec.getInputBuffer(inputBufferIndex);
      } catch (RuntimeException e) {
        Log.d(TAG, "MediaCodec error", e);
        throw createExportException(e);
      }
      inputBuffer.clear();
    }
    checkNotNull(inputBuffer.data);
    return true;
  }

  @Override
  public void queueInputBuffer(DecoderInputBuffer inputBuffer) throws ExportException {
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
      if (isVideo && isDecoder) {
        DebugTraceUtil.recordDecoderReceiveEos();
      }
    }
    try {
      mediaCodec.queueInputBuffer(inputBufferIndex, offset, size, inputBuffer.timeUs, flags);
    } catch (RuntimeException e) {
      Log.d(TAG, "MediaCodec error", e);
      throw createExportException(e);
    }
    inputBufferIndex = C.INDEX_UNSET;
    inputBuffer.data = null;
  }

  @Override
  public void signalEndOfInputStream() throws ExportException {
    DebugTraceUtil.recordEncoderReceiveEos();
    try {
      mediaCodec.signalEndOfInputStream();
    } catch (RuntimeException e) {
      Log.d(TAG, "MediaCodec error", e);
      throw createExportException(e);
    }
  }

  @Override
  @Nullable
  public Format getOutputFormat() throws ExportException {
    // The format is updated when dequeueing a 'special' buffer index, so attempt to dequeue now.
    maybeDequeueOutputBuffer(/* setOutputBuffer= */ false);
    return outputFormat;
  }

  @Override
  @Nullable
  public ByteBuffer getOutputBuffer() throws ExportException {
    return maybeDequeueOutputBuffer(/* setOutputBuffer= */ true) ? outputBuffer : null;
  }

  @Override
  @Nullable
  public BufferInfo getOutputBufferInfo() throws ExportException {
    return maybeDequeueOutputBuffer(/* setOutputBuffer= */ false) ? outputBufferInfo : null;
  }

  @Override
  public void releaseOutputBuffer(boolean render) throws ExportException {
    releaseOutputBuffer(render, checkStateNotNull(outputBufferInfo).presentationTimeUs);
  }

  @Override
  public void releaseOutputBuffer(long renderPresentationTimeUs) throws ExportException {
    releaseOutputBuffer(/* render= */ true, renderPresentationTimeUs);
  }

  private void releaseOutputBuffer(boolean render, long renderPresentationTimeUs)
      throws ExportException {
    outputBuffer = null;
    try {
      if (render) {
        mediaCodec.releaseOutputBuffer(
            outputBufferIndex, /* renderTimestampNs= */ renderPresentationTimeUs * 1000);
      } else {
        mediaCodec.releaseOutputBuffer(outputBufferIndex, /* render= */ false);
      }
    } catch (RuntimeException e) {
      Log.d(TAG, "MediaCodec error", e);
      throw createExportException(e);
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
   * @throws ExportException If the underlying {@link MediaCodec} encounters a problem.
   */
  private boolean maybeDequeueOutputBuffer(boolean setOutputBuffer) throws ExportException {
    if (outputBufferIndex >= 0) {
      return true;
    }
    if (outputStreamEnded) {
      return false;
    }

    try {
      outputBufferIndex = mediaCodec.dequeueOutputBuffer(outputBufferInfo, /* timeoutUs= */ 0);
    } catch (RuntimeException e) {
      Log.d(TAG, "MediaCodec error", e);
      throw createExportException(e);
    }
    if (outputBufferIndex < 0) {
      if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        outputFormat = convertToFormat(mediaCodec.getOutputFormat(), isDecoder);
      }
      return false;
    }
    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      outputStreamEnded = true;
      if (outputBufferInfo.size == 0) {
        releaseOutputBuffer(/* render= */ false);
        return false;
      }
      outputBufferInfo.flags &= ~MediaCodec.BUFFER_FLAG_END_OF_STREAM;
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
        Log.d(TAG, "MediaCodec error", e);
        throw createExportException(e);
      }
      outputBuffer.position(outputBufferInfo.offset);
      outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
    }
    return true;
  }

  private ExportException createExportException(Exception cause) {
    return createExportException(
        cause,
        isDecoder
            ? ExportException.ERROR_CODE_DECODING_FAILED
            : ExportException.ERROR_CODE_ENCODING_FAILED,
        getName());
  }

  /** Creates an {@link ExportException} with specific {@link MediaCodec} details. */
  private ExportException createExportException(
      @UnknownInitialization DefaultCodec this,
      Exception cause,
      @ExportException.ErrorCode int errorCode,
      String mediaCodecName) {
    String codecDetails =
        "mediaFormat=" + configurationMediaFormat + ", mediaCodecName=" + mediaCodecName;
    return ExportException.createForCodec(cause, errorCode, isVideo, isDecoder, codecDetails);
  }

  private static Format convertToFormat(MediaFormat mediaFormat, boolean isDecoder) {
    Format.Builder formatBuilder =
        MediaFormatUtil.createFormatFromMediaFormat(mediaFormat).buildUpon();
    if (isDecoder) {
      // TODO(b/178685617): Restrict this to only set the PCM encoding for audio/raw once we have
      // a way to simulate more realistic codec input/output formats in tests.

      // With Robolectric, codecs do not actually encode/decode. The format of buffers is passed
      // through. However downstream components need to know the PCM encoding of the data being
      // output, so if a decoder is not outputting raw audio, we need to set the PCM
      // encoding to the default.
      formatBuilder.setPcmEncoding(DEFAULT_PCM_ENCODING);
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

  private static boolean isSdrToneMappingEnabled(MediaFormat mediaFormat) {
    // MediaFormat.KEY_COLOR_TRANSFER_REQUEST was added in API 31.
    return SDK_INT >= 31
        && MediaFormatUtil.getInteger(
                mediaFormat, MediaFormat.KEY_COLOR_TRANSFER_REQUEST, /* defaultValue= */ 0)
            == MediaFormat.COLOR_TRANSFER_SDR_VIDEO;
  }

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static String getCanonicalName(MediaCodec mediaCodec) {
      return mediaCodec.getCanonicalName();
    }
  }
}
