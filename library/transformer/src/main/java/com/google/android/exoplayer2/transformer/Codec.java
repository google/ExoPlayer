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

import android.media.MediaCodec.BufferInfo;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Provides a layer of abstraction for interacting with decoders and encoders.
 *
 * <p>{@link DecoderInputBuffer DecoderInputBuffers} are used as both decoders' and encoders' input
 * buffers.
 */
public interface Codec {
  /** Default value for the pending frame count, which represents applying no limit. */
  int UNLIMITED_PENDING_FRAME_COUNT = Integer.MAX_VALUE;

  /** A factory for {@linkplain Codec decoder} instances. */
  interface DecoderFactory {

    /**
     * Returns a {@link Codec} for audio decoding.
     *
     * @param format The {@link Format} (of the input data) used to determine the underlying decoder
     *     and its configuration values.
     * @return A {@link Codec} for audio decoding.
     * @throws TransformationException If no suitable {@link Codec} can be created.
     */
    Codec createForAudioDecoding(Format format) throws TransformationException;

    /**
     * Returns a {@link Codec} for video decoding.
     *
     * @param format The {@link Format} (of the input data) used to determine the underlying decoder
     *     and its configuration values.
     * @param outputSurface The {@link Surface} to which the decoder output is rendered.
     * @param enableRequestSdrToneMapping Whether to request tone-mapping to SDR.
     * @return A {@link Codec} for video decoding.
     * @throws TransformationException If no suitable {@link Codec} can be created.
     */
    Codec createForVideoDecoding(
        Format format, Surface outputSurface, boolean enableRequestSdrToneMapping)
        throws TransformationException;
  }

  /** A factory for {@linkplain Codec encoder} instances. */
  interface EncoderFactory {

    /**
     * Returns a {@link Codec} for audio encoding.
     *
     * <p>This method must validate that the {@link Codec} is configured to produce one of the
     * {@code allowedMimeTypes}. The {@linkplain Format#sampleMimeType sample MIME type} given in
     * {@code format} is not necessarily allowed.
     *
     * @param format The {@link Format} (of the output data) used to determine the underlying
     *     encoder and its configuration values.
     * @param allowedMimeTypes The non-empty list of allowed output sample {@linkplain MimeTypes
     *     MIME types}.
     * @return A {@link Codec} for audio encoding.
     * @throws TransformationException If no suitable {@link Codec} can be created.
     */
    Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException;

    /**
     * Returns a {@link Codec} for video encoding.
     *
     * <p>This method must validate that the {@link Codec} is configured to produce one of the
     * {@code allowedMimeTypes}. The {@linkplain Format#sampleMimeType sample MIME type} given in
     * {@code format} is not necessarily allowed.
     *
     * @param format The {@link Format} (of the output data) used to determine the underlying
     *     encoder and its configuration values. {@link Format#sampleMimeType}, {@link Format#width}
     *     and {@link Format#height} are set to those of the desired output video format. {@link
     *     Format#rotationDegrees} is 0 and {@link Format#width} {@code >=} {@link Format#height},
     *     therefore the video is always in landscape orientation. {@link Format#frameRate} is set
     *     to the output video's frame rate, if available.
     * @param allowedMimeTypes The non-empty list of allowed output sample {@linkplain MimeTypes
     *     MIME types}.
     * @return A {@link Codec} for video encoding.
     * @throws TransformationException If no suitable {@link Codec} can be created.
     */
    Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException;

    /** Returns whether the audio needs to be encoded because of encoder specific configuration. */
    default boolean audioNeedsEncoding() {
      return false;
    }

    /** Returns whether the video needs to be encoded because of encoder specific configuration. */
    default boolean videoNeedsEncoding() {
      return false;
    }
  }

  /**
   * Returns the {@link Format} used for configuring the {@code Codec}.
   *
   * <p>The configuration {@link Format} is the input {@link Format} used by the {@link
   * DecoderFactory} or output {@link Format} used by the {@link EncoderFactory} for selecting and
   * configuring the underlying decoder or encoder.
   */
  Format getConfigurationFormat();

  /** Returns the name of the underlying codec. */
  String getName();

  /**
   * Returns the input {@link Surface} of an underlying video encoder.
   *
   * <p>This method must only be called on video encoders because audio/video decoders and audio
   * encoders don't use a {@link Surface} as input.
   */
  Surface getInputSurface();

  /**
   * Returns the maximum number of frames that may be pending in the output {@code Codec} at a time,
   * or {@link #UNLIMITED_PENDING_FRAME_COUNT} if it's not necessary to enforce a limit.
   */
  default int getMaxPendingFrameCount() {
    return UNLIMITED_PENDING_FRAME_COUNT;
  }

  /**
   * Dequeues a writable input buffer, if available.
   *
   * <p>This method must not be called from video encoders because they must use a {@link Surface}
   * to receive input.
   *
   * @param inputBuffer The buffer where the dequeued buffer data is stored, at {@link
   *     DecoderInputBuffer#data inputBuffer.data}.
   * @return Whether an input buffer is ready to be used.
   * @throws TransformationException If the underlying decoder or encoder encounters a problem.
   */
  boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer) throws TransformationException;

  /**
   * Queues an input buffer to the {@code Codec}. No buffers may be queued after {@linkplain
   * DecoderInputBuffer#isEndOfStream() end of stream} buffer has been queued.
   *
   * <p>This method must not be called from video encoders because they must use a {@link Surface}
   * to receive input.
   *
   * @param inputBuffer The {@linkplain DecoderInputBuffer input buffer}.
   * @throws TransformationException If the underlying decoder or encoder encounters a problem.
   */
  void queueInputBuffer(DecoderInputBuffer inputBuffer) throws TransformationException;

  /**
   * Signals end-of-stream on input to a video encoder.
   *
   * <p>This method must only be called on video encoders because they must use a {@link Surface} as
   * input. For audio/video decoders or audio encoders, the {@link C#BUFFER_FLAG_END_OF_STREAM} flag
   * should be set on the last input buffer {@linkplain #queueInputBuffer(DecoderInputBuffer)
   * queued}.
   *
   * @throws TransformationException If the underlying video encoder encounters a problem.
   */
  void signalEndOfInputStream() throws TransformationException;

  /**
   * Returns the current output format, or {@code null} if unavailable.
   *
   * @throws TransformationException If the underlying decoder or encoder encounters a problem.
   */
  @Nullable
  Format getOutputFormat() throws TransformationException;

  /**
   * Returns the current output {@link ByteBuffer}, or {@code null} if unavailable.
   *
   * <p>This method must not be called on video decoders because they must output to a {@link
   * Surface}.
   *
   * @throws TransformationException If the underlying decoder or encoder encounters a problem.
   */
  @Nullable
  ByteBuffer getOutputBuffer() throws TransformationException;

  /**
   * Returns the {@link BufferInfo} associated with the current output buffer, or {@code null} if
   * there is no output buffer available.
   *
   * <p>This method returns {@code null} if and only if {@link #getOutputBuffer()} returns null.
   *
   * @throws TransformationException If the underlying decoder or encoder encounters a problem.
   */
  @Nullable
  BufferInfo getOutputBufferInfo() throws TransformationException;

  /**
   * Releases the current output buffer.
   *
   * <p>Only set {@code render} to {@code true} when the {@code Codec} is a video decoder. Setting
   * {@code render} to {@code true} will first render the buffer to the output surface. In this
   * case, the surface will release the buffer back to the {@code Codec} once it is no longer
   * used/displayed.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the current output buffer has been released.
   *
   * @param render Whether the buffer needs to be rendered to the output {@link Surface}.
   * @throws TransformationException If the underlying decoder or encoder encounters a problem.
   */
  void releaseOutputBuffer(boolean render) throws TransformationException;

  /**
   * Returns whether the {@code Codec}'s output stream has ended, and no more data can be dequeued.
   */
  boolean isEnded();

  /** Releases the {@code Codec}. */
  void release();
}
