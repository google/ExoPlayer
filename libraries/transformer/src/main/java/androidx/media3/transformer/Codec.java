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

package androidx.media3.transformer;

import android.media.MediaCodec.BufferInfo;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;

/**
 * Provides a layer of abstraction for interacting with decoders and encoders.
 *
 * <p>{@link DecoderInputBuffer DecoderInputBuffers} are used as both decoders' and encoders' input
 * buffers.
 */
@UnstableApi
public interface Codec {
  /** A factory for {@linkplain Codec decoder} instances. */
  interface DecoderFactory {

    /**
     * Returns a {@link Codec} for audio decoding.
     *
     * @param format The {@link Format} (of the input data) used to determine the underlying decoder
     *     and its configuration values.
     * @return A {@link Codec} for audio decoding.
     * @throws ExportException If no suitable {@link Codec} can be created.
     */
    Codec createForAudioDecoding(Format format) throws ExportException;

    /**
     * Returns a {@link Codec} for video decoding.
     *
     * @param format The {@link Format} (of the input data) used to determine the underlying decoder
     *     and its configuration values.
     * @param outputSurface The {@link Surface} to which the decoder output is rendered.
     * @param requestSdrToneMapping Whether to request tone-mapping to SDR.
     * @return A {@link Codec} for video decoding.
     * @throws ExportException If no suitable {@link Codec} can be created.
     */
    Codec createForVideoDecoding(
        Format format, Surface outputSurface, boolean requestSdrToneMapping) throws ExportException;
  }

  /** A factory for {@linkplain Codec encoder} instances. */
  interface EncoderFactory {

    /**
     * Returns a {@link Codec} for audio encoding.
     *
     * <p>The caller should ensure the {@linkplain Format#sampleMimeType MIME type} is supported on
     * the device before calling this method.
     *
     * <p>{@link Format#codecs} contains the codec string for the original input media that has been
     * decoded and processed. This is provided only as a hint, and the factory may encode to a
     * different format.
     *
     * @param format The {@link Format} (of the output data) used to determine the underlying
     *     encoder and its configuration values. {@link Format#sampleMimeType}, {@link
     *     Format#sampleRate}, {@link Format#channelCount} and {@link Format#bitrate} are set to
     *     those of the desired output video format.
     * @return A {@link Codec} for encoding audio to the requested {@link Format#sampleMimeType MIME
     *     type}.
     * @throws ExportException If no suitable {@link Codec} can be created.
     */
    Codec createForAudioEncoding(Format format) throws ExportException;

    /**
     * Returns a {@link Codec} for video encoding.
     *
     * <p>The caller should ensure the {@linkplain Format#sampleMimeType MIME type} is supported on
     * the device before calling this method. If encoding to HDR, the caller should also ensure the
     * {@linkplain Format#colorInfo color characteristics} are supported.
     *
     * <p>{@link Format#codecs} contains the codec string for the original input media that has been
     * decoded and processed. This is provided only as a hint, and the factory may encode to a
     * different format.
     *
     * @param format The {@link Format} (of the output data) used to determine the underlying
     *     encoder and its configuration values. {@link Format#sampleMimeType}, {@link Format#width}
     *     and {@link Format#height} are set to those of the desired output video format. {@link
     *     Format#frameRate} is set to the requested output frame rate, if available. {@link
     *     Format#colorInfo} is set to the requested output color characteristics, if available.
     *     {@link Format#rotationDegrees} is 0 and {@link Format#width} {@code >=} {@link
     *     Format#height}, therefore the video is always in landscape orientation.
     * @return A {@link Codec} for encoding video to the requested {@linkplain Format#sampleMimeType
     *     MIME type}.
     * @throws ExportException If no suitable {@link Codec} can be created.
     */
    Codec createForVideoEncoding(Format format) throws ExportException;

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
   * or {@code 5} as a default value.
   */
  default int getMaxPendingFrameCount() {
    return 5;
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
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  boolean maybeDequeueInputBuffer(DecoderInputBuffer inputBuffer) throws ExportException;

  /**
   * Queues an input buffer to the {@code Codec}. No buffers may be queued after {@linkplain
   * DecoderInputBuffer#isEndOfStream() end of stream} buffer has been queued.
   *
   * <p>This method must not be called from video encoders because they must use a {@link Surface}
   * to receive input.
   *
   * @param inputBuffer The {@linkplain DecoderInputBuffer input buffer}.
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  void queueInputBuffer(DecoderInputBuffer inputBuffer) throws ExportException;

  /**
   * Signals end-of-stream on input to a video encoder.
   *
   * <p>This method must only be called on video encoders because they must use a {@link Surface} as
   * input. For audio/video decoders or audio encoders, the {@link C#BUFFER_FLAG_END_OF_STREAM} flag
   * should be set on the last input buffer {@linkplain #queueInputBuffer(DecoderInputBuffer)
   * queued}.
   *
   * @throws ExportException If the underlying video encoder encounters a problem.
   */
  void signalEndOfInputStream() throws ExportException;

  /**
   * Returns the current output format, or {@code null} if unavailable.
   *
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  @Nullable
  Format getOutputFormat() throws ExportException;

  /**
   * Returns the current output {@link ByteBuffer}, or {@code null} if unavailable.
   *
   * <p>This method must not be called on video decoders because they must output to a {@link
   * Surface}.
   *
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  @Nullable
  ByteBuffer getOutputBuffer() throws ExportException;

  /**
   * Returns the {@link BufferInfo} associated with the current output buffer, or {@code null} if
   * there is no output buffer available.
   *
   * <p>This method returns {@code null} if and only if {@link #getOutputBuffer()} returns null.
   *
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  @Nullable
  BufferInfo getOutputBufferInfo() throws ExportException;

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
   * <p>Calling this method with {@code render} set to {@code true} is equivalent to calling {@link
   * #releaseOutputBuffer(long)} with the presentation timestamp of the {@link
   * #getOutputBufferInfo() output buffer info}.
   *
   * @param render Whether the buffer needs to be rendered to the output {@link Surface}.
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  void releaseOutputBuffer(boolean render) throws ExportException;

  /**
   * Renders and releases the current output buffer.
   *
   * <p>This method must only be called on video decoders.
   *
   * <p>This method will first render the buffer to the output surface. The surface will then
   * release the buffer back to the {@code Codec} once it is no longer used/displayed.
   *
   * <p>This should be called after the buffer has been processed. The next output buffer will not
   * be available until the current output buffer has been released.
   *
   * @param renderPresentationTimeUs The presentation timestamp to associate with this buffer, in
   *     microseconds.
   * @throws ExportException If the underlying decoder or encoder encounters a problem.
   */
  void releaseOutputBuffer(long renderPresentationTimeUs) throws ExportException;

  /**
   * Returns whether the {@code Codec}'s output stream has ended, and no more data can be dequeued.
   */
  boolean isEnded();

  /** Releases the {@code Codec}. */
  void release();
}
