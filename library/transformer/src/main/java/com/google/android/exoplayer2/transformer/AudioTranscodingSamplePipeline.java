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

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessingPipeline;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to decode audio samples, apply audio processing to the raw samples, and re-encode them.
 */
/* package */ final class AudioTranscodingSamplePipeline extends BaseSamplePipeline {

  private static final int DEFAULT_ENCODER_BITRATE = 128 * 1024;

  private final Codec decoder;
  private final DecoderInputBuffer decoderInputBuffer;
  private final AudioProcessingPipeline audioProcessingPipeline;
  private final Codec encoder;
  private final AudioFormat encoderInputAudioFormat;
  private final DecoderInputBuffer encoderInputBuffer;
  private final DecoderInputBuffer encoderOutputBuffer;

  private long nextEncoderInputBufferTimeUs;
  private long encoderBufferDurationRemainder;

  public AudioTranscodingSamplePipeline(
      Format inputFormat,
      long streamStartPositionUs,
      long streamOffsetUs,
      TransformationRequest transformationRequest,
      ImmutableList<AudioProcessor> audioProcessors,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      Listener listener,
      FallbackListener fallbackListener)
      throws TransformationException {
    super(
        inputFormat,
        streamStartPositionUs,
        streamOffsetUs,
        transformationRequest.flattenForSlowMotion,
        muxerWrapper,
        listener);

    decoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);

    decoder = decoderFactory.createForAudioDecoding(inputFormat);

    if (transformationRequest.flattenForSlowMotion) {
      audioProcessors =
          new ImmutableList.Builder<AudioProcessor>()
              .add(new SpeedChangingAudioProcessor(new SegmentSpeedProvider(inputFormat)))
              .addAll(audioProcessors)
              .build();
    }

    audioProcessingPipeline = new AudioProcessingPipeline(audioProcessors);
    AudioFormat pipelineInputAudioFormat =
        new AudioFormat(
            inputFormat.sampleRate,
            inputFormat.channelCount,
            // The decoder uses ENCODING_PCM_16BIT by default.
            // https://developer.android.com/reference/android/media/MediaCodec#raw-audio-buffers
            C.ENCODING_PCM_16BIT);

    try {
      encoderInputAudioFormat = audioProcessingPipeline.configure(pipelineInputAudioFormat);
    } catch (AudioProcessor.UnhandledAudioFormatException unhandledAudioFormatException) {
      throw TransformationException.createForAudioProcessing(
          unhandledAudioFormatException, pipelineInputAudioFormat);
    }

    audioProcessingPipeline.flush();

    Format requestedOutputFormat =
        new Format.Builder()
            .setSampleMimeType(
                transformationRequest.audioMimeType == null
                    ? inputFormat.sampleMimeType
                    : transformationRequest.audioMimeType)
            .setSampleRate(encoderInputAudioFormat.sampleRate)
            .setChannelCount(encoderInputAudioFormat.channelCount)
            .setAverageBitrate(DEFAULT_ENCODER_BITRATE)
            .build();
    encoder =
        encoderFactory.createForAudioEncoding(
            requestedOutputFormat, muxerWrapper.getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO));

    fallbackListener.onTransformationRequestFinalized(
        createFallbackTransformationRequest(
            transformationRequest, requestedOutputFormat, encoder.getConfigurationFormat()));

    // Use the same stream offset as the input stream for encoder input buffers.
    nextEncoderInputBufferTimeUs = streamOffsetUs;
  }

  @Override
  public void release() {
    audioProcessingPipeline.reset();
    decoder.release();
    encoder.release();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer dequeueInputBufferInternal() throws TransformationException {
    return decoder.maybeDequeueInputBuffer(decoderInputBuffer) ? decoderInputBuffer : null;
  }

  @Override
  protected void queueInputBufferInternal() throws TransformationException {
    decoder.queueInputBuffer(decoderInputBuffer);
  }

  @Override
  protected boolean processDataUpToMuxer() throws TransformationException {
    if (audioProcessingPipeline.isOperational()) {
      return feedEncoderFromProcessingPipeline() || feedProcessingPipelineFromDecoder();
    } else {
      return feedEncoderFromDecoder();
    }
  }

  @Override
  @Nullable
  protected Format getMuxerInputFormat() throws TransformationException {
    return encoder.getOutputFormat();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() throws TransformationException {
    encoderOutputBuffer.data = encoder.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    encoderOutputBuffer.timeUs = checkNotNull(encoder.getOutputBufferInfo()).presentationTimeUs;
    encoderOutputBuffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    return encoderOutputBuffer;
  }

  @Override
  protected void releaseMuxerInputBuffer() throws TransformationException {
    encoder.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return encoder.isEnded();
  }

  /**
   * Attempts to pass decoder output data to the encoder, and returns whether it may be possible to
   * pass more data immediately by calling this method again.
   */
  private boolean feedEncoderFromDecoder() throws TransformationException {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (decoder.isEnded()) {
      queueEndOfStreamToEncoder();
      return false;
    }

    @Nullable ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer();
    if (decoderOutputBuffer == null) {
      return false;
    }

    feedEncoder(decoderOutputBuffer);
    if (!decoderOutputBuffer.hasRemaining()) {
      decoder.releaseOutputBuffer(/* render= */ false);
    }
    return true;
  }

  /**
   * Attempts to feed audio processor output data to the encoder.
   *
   * @return Whether more data can be fed immediately, by calling this method again.
   */
  private boolean feedEncoderFromProcessingPipeline() throws TransformationException {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    ByteBuffer processingPipelineOutputBuffer = audioProcessingPipeline.getOutput();

    if (!processingPipelineOutputBuffer.hasRemaining()) {
      if (audioProcessingPipeline.isEnded()) {
        queueEndOfStreamToEncoder();
      }
      return false;
    }

    feedEncoder(processingPipelineOutputBuffer);
    return true;
  }

  /**
   * Attempts to feed decoder output data to the {@link AudioProcessingPipeline}.
   *
   * @return Whether it may be possible to feed more data immediately by calling this method again.
   */
  private boolean feedProcessingPipelineFromDecoder() throws TransformationException {
    if (decoder.isEnded()) {
      audioProcessingPipeline.queueEndOfStream();
      return false;
    }
    checkState(!audioProcessingPipeline.isEnded());

    @Nullable ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer();
    if (decoderOutputBuffer == null) {
      return false;
    }

    audioProcessingPipeline.queueInput(decoderOutputBuffer);
    if (decoderOutputBuffer.hasRemaining()) {
      return false;
    }
    // Decoder output buffer was fully consumed by the processing pipeline.
    decoder.releaseOutputBuffer(/* render= */ false);
    return true;
  }

  /**
   * Feeds as much data as possible between the current position and limit of the specified {@link
   * ByteBuffer} to the encoder, and advances its position by the number of bytes fed.
   */
  private void feedEncoder(ByteBuffer inputBuffer) throws TransformationException {
    ByteBuffer encoderInputBufferData = checkNotNull(encoderInputBuffer.data);
    int bufferLimit = inputBuffer.limit();
    inputBuffer.limit(min(bufferLimit, inputBuffer.position() + encoderInputBufferData.capacity()));
    encoderInputBufferData.put(inputBuffer);
    encoderInputBuffer.timeUs = nextEncoderInputBufferTimeUs;
    computeNextEncoderInputBufferTimeUs(
        /* bytesWritten= */ encoderInputBufferData.position(),
        encoderInputAudioFormat.bytesPerFrame,
        encoderInputAudioFormat.sampleRate);
    encoderInputBuffer.setFlags(0);
    encoderInputBuffer.flip();
    inputBuffer.limit(bufferLimit);
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  private void queueEndOfStreamToEncoder() throws TransformationException {
    checkState(checkNotNull(encoderInputBuffer.data).position() == 0);
    encoderInputBuffer.timeUs = nextEncoderInputBufferTimeUs;
    encoderInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    encoderInputBuffer.flip();
    // Queuing EOS should only occur with an empty buffer.
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  @Pure
  private static TransformationRequest createFallbackTransformationRequest(
      TransformationRequest transformationRequest, Format requestedFormat, Format actualFormat) {
    // TODO(b/210591626): Also update bitrate and other params once encoder configuration and
    // fallback are implemented.
    if (Util.areEqual(requestedFormat.sampleMimeType, actualFormat.sampleMimeType)) {
      return transformationRequest;
    }
    return transformationRequest.buildUpon().setAudioMimeType(actualFormat.sampleMimeType).build();
  }

  private void computeNextEncoderInputBufferTimeUs(
      long bytesWritten, int bytesPerFrame, int sampleRate) {
    // The calculation below accounts for remainders and rounding. Without that it corresponds to
    // the following:
    // bufferDurationUs = numberOfFramesInBuffer * sampleDurationUs
    //     where numberOfFramesInBuffer = bytesWritten / bytesPerFrame
    //     and   sampleDurationUs       = C.MICROS_PER_SECOND / sampleRate
    long numerator = bytesWritten * C.MICROS_PER_SECOND + encoderBufferDurationRemainder;
    long denominator = (long) bytesPerFrame * sampleRate;
    long bufferDurationUs = numerator / denominator;
    encoderBufferDurationRemainder = numerator - bufferDurationUs * denominator;
    if (encoderBufferDurationRemainder > 0) { // Ceil division result.
      bufferDurationUs += 1;
      encoderBufferDurationRemainder -= denominator;
    }
    nextEncoderInputBufferTimeUs += bufferDurationUs;
  }
}
