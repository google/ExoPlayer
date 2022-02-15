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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A wrapper around {@link MediaCodec}.
 *
 * <p>Provides a layer of abstraction for callers that need to interact with {@link MediaCodec}.
 * This is done by simplifying the calls needed to queue and dequeue buffers, removing the need to
 * track buffer indices and codec events.
 */
public final class Codec {

  /** A factory for {@link Codec decoder} instances. */
  public interface DecoderFactory {

    /** A default {@code DecoderFactory} implementation. */
    DecoderFactory DEFAULT = new DefaultCodecFactory();

    /**
     * Returns a {@link Codec} for audio decoding.
     *
     * @param format The {@link Format} (of the input data) used to determine the underlying {@link
     *     MediaCodec} and its configuration values.
     * @return A configured and started decoder wrapper.
     * @throws TransformationException If no suitable codec can be created.
     */
    Codec createForAudioDecoding(Format format) throws TransformationException;

    /**
     * Returns a {@link Codec} for video decoding.
     *
     * @param format The {@link Format} (of the input data) used to determine the underlying {@link
     *     MediaCodec} and its configuration values.
     * @param outputSurface The {@link Surface} to which the decoder output is rendered.
     * @return A configured and started decoder wrapper.
     * @throws TransformationException If no suitable codec can be created.
     */
    Codec createForVideoDecoding(Format format, Surface outputSurface)
        throws TransformationException;
  }

  /** A factory for {@link Codec encoder} instances. */
  public interface EncoderFactory {

    /** A default {@code EncoderFactory} implementation. */
    EncoderFactory DEFAULT = new DefaultCodecFactory();

    /**
     * Returns a {@link Codec} for audio encoding.
     *
     * <p>This method must validate that the {@link Codec} is configured to produce one of the
     * {@code allowedMimeTypes}. The {@link Format#sampleMimeType sample MIME type} given in {@code
     * format} is not necessarily allowed.
     *
     * @param format The {@link Format} (of the output data) used to determine the underlying {@link
     *     MediaCodec} and its configuration values.
     * @param allowedMimeTypes The non-empty list of allowed output sample {@link MimeTypes MIME
     *     types}.
     * @return A configured and started encoder wrapper.
     * @throws TransformationException If no suitable codec can be created.
     */
    Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException;

    /**
     * Returns a {@link Codec} for video encoding.
     *
     * <p>This method must validate that the {@link Codec} is configured to produce one of the
     * {@code allowedMimeTypes}. The {@link Format#sampleMimeType sample MIME type} given in {@code
     * format} is not necessarily allowed.
     *
     * @param format The {@link Format} (of the output data) used to determine the underlying {@link
     *     MediaCodec} and its configuration values. {@link Format#sampleMimeType}, {@link
     *     Format#width} and {@link Format#height} must be set to those of the desired output video
     *     format. {@link Format#rotationDegrees} should be 0. The video should always be in
     *     landscape orientation.
     * @param allowedMimeTypes The non-empty list of allowed output sample {@link MimeTypes MIME
     *     types}.
     * @return A configured and started encoder wrapper.
     * @throws TransformationException If no suitable codec can be created.
     */
    Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException;
  }

  // MediaCodec decoders always output 16 bit PCM, unless configured to output PCM float.
  // https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers.
  private static final int MEDIA_CODEC_PCM_ENCODING = C.ENCODING_PCM_16BIT;

  private final BufferInfo outputBufferInfo;
  private final MediaCodec mediaCodec;
  private final Format configurationFormat;
  @Nullable private final Surface inputSurface;

  private @MonotonicNonNull Format outputFormat;
  @Nullable private ByteBuffer outputBuffer;

  private int inputBufferIndex;
  private int outputBufferIndex;
  private boolean inputStreamEnded;
  private boolean outputStreamEnded;

  /**
   * Creates a {@code Codec} from a configured and started {@link MediaCodec}.
   *
   * @param mediaCodec The configured and started {@link MediaCodec}.
   * @param configurationFormat See {@link #getConfigurationFormat()}.
   * @param inputSurface The input {@link Surface} if the {@link MediaCodec} receives input from a
   *     surface.
   */
  public Codec(MediaCodec mediaCodec, Format configurationFormat, @Nullable Surface inputSurface) {
    this.mediaCodec = mediaCodec;
    this.configurationFormat = configurationFormat;
    this.inputSurface = inputSurface;
    outputBufferInfo = new BufferInfo();
    inputBufferIndex = C.INDEX_UNSET;
    outputBufferIndex = C.INDEX_UNSET;
  }

  /**
   * Returns the {@link Format} used for configuring the codec.
   *
   * <p>The configuration {@link Format} is the input {@link Format} used by the {@link
   * DecoderFactory} or output {@link Format} used by the {@link EncoderFactory} for selecting and
   * configuring the underlying {@link MediaCodec}.
   */
  public Format getConfigurationFormat() {
    return configurationFormat;
  }

  /** Returns the input {@link Surface}, or null if the input is not a surface. */
  @Nullable
  public Surface getInputSurface() {
    return inputSurface;
  }

  /**
   * Dequeues a writable input buffer, if available.
   *
   * @param inputBuffer The buffer where the dequeued buffer data is stored.
   * @return Whether an input buffer is ready to be used.
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
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

  /**
   * Queues an input buffer to the decoder. No buffers may be queued after an {@link
   * DecoderInputBuffer#isEndOfStream() end of stream} buffer has been queued.
   *
   * @param inputBuffer The {@link DecoderInputBuffer input buffer}.
   * @throws IllegalStateException If called again after an {@link
   *     DecoderInputBuffer#isEndOfStream() end of stream} buffer has been queued.
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
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

  /**
   * Signals end-of-stream on input to a video encoder.
   *
   * <p>This method does not need to be called for audio/video decoders or audio encoders. For these
   * the {@link MediaCodec#BUFFER_FLAG_END_OF_STREAM} flag should be set on the last input buffer
   * {@link #queueInputBuffer(DecoderInputBuffer) queued}.
   *
   * @throws IllegalStateException If the codec is not an encoder receiving input from a {@link
   *     Surface}.
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
  public void signalEndOfInputStream() throws TransformationException {
    checkState(mediaCodec.getCodecInfo().isEncoder() && inputSurface != null);
    try {
      mediaCodec.signalEndOfInputStream();
    } catch (RuntimeException e) {
      throw createTransformationException(e);
    }
  }

  /**
   * Returns the current output format, if available.
   *
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
  @Nullable
  public Format getOutputFormat() throws TransformationException {
    // The format is updated when dequeueing a 'special' buffer index, so attempt to dequeue now.
    maybeDequeueOutputBuffer(/* setOutputBuffer= */ false);
    return outputFormat;
  }

  /**
   * Returns the current output {@link ByteBuffer}, if available.
   *
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
  @Nullable
  public ByteBuffer getOutputBuffer() throws TransformationException {
    return maybeDequeueOutputBuffer(/* setOutputBuffer= */ true) ? outputBuffer : null;
  }

  /**
   * Returns the {@link BufferInfo} associated with the current output buffer, if available.
   *
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
  @Nullable
  public BufferInfo getOutputBufferInfo() throws TransformationException {
    return maybeDequeueOutputBuffer(/* setOutputBuffer= */ false) ? outputBufferInfo : null;
  }

  /**
   * Releases the current output buffer.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the previous has been released.
   *
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
  public void releaseOutputBuffer() throws TransformationException {
    releaseOutputBuffer(/* render= */ false);
  }

  /**
   * Releases the current output buffer. If the {@link MediaCodec} was configured with an output
   * surface, setting {@code render} to {@code true} will first send the buffer to the output
   * surface. The surface will release the buffer back to the codec once it is no longer
   * used/displayed.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the previous has been released.
   *
   * @param render Whether the buffer needs to be sent to the output {@link Surface}.
   * @throws TransformationException If the underlying {@link MediaCodec} encounters a problem.
   */
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

  /** Returns whether the codec output stream has ended, and no more data can be dequeued. */
  public boolean isEnded() {
    return outputStreamEnded && outputBufferIndex == C.INDEX_UNSET;
  }

  /** Releases the underlying codec. */
  public void release() {
    outputBuffer = null;
    if (inputSurface != null) {
      inputSurface.release();
    }
    mediaCodec.release();
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
        outputFormat = getFormat(mediaCodec.getOutputFormat());
      }
      return false;
    }
    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
      outputStreamEnded = true;
      if (outputBufferInfo.size == 0) {
        releaseOutputBuffer();
        return false;
      }
    }
    if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
      // Encountered a CSD buffer, skip it.
      releaseOutputBuffer();
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
    boolean isEncoder = mediaCodec.getCodecInfo().isEncoder();
    boolean isVideo = MimeTypes.isVideo(configurationFormat.sampleMimeType);
    String componentName = (isVideo ? "Video" : "Audio") + (isEncoder ? "Encoder" : "Decoder");
    return TransformationException.createForCodec(
        cause,
        componentName,
        configurationFormat,
        mediaCodec.getName(),
        isEncoder
            ? TransformationException.ERROR_CODE_ENCODING_FAILED
            : TransformationException.ERROR_CODE_DECODING_FAILED);
  }

  private static Format getFormat(MediaFormat mediaFormat) {
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
        new Format.Builder()
            .setSampleMimeType(mediaFormat.getString(MediaFormat.KEY_MIME))
            .setInitializationData(csdBuffers.build());
    if (MimeTypes.isVideo(mimeType)) {
      formatBuilder
          .setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH))
          .setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
    } else if (MimeTypes.isAudio(mimeType)) {
      // TODO(internal b/178685617): Only set the PCM encoding for audio/raw, once we have a way to
      // simulate more realistic codec input/output formats in tests.
      formatBuilder
          .setChannelCount(mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
          .setSampleRate(mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE))
          .setPcmEncoding(MEDIA_CODEC_PCM_ENCODING);
    }
    return formatBuilder.build();
  }
}
