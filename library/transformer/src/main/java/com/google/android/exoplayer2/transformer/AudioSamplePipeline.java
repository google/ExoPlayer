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
import static java.lang.Math.min;

import android.media.MediaCodec.BufferInfo;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Pipeline to decode audio samples, apply transformations on the raw samples, and re-encode them.
 */
@RequiresApi(18)
/* package */ final class AudioSamplePipeline implements SamplePipeline {

  private static final String TAG = "AudioSamplePipeline";
  private static final int DEFAULT_ENCODER_BITRATE = 128 * 1024;

  private final MediaCodecAdapterWrapper decoder;
  private final Format decoderInputFormat;
  private final DecoderInputBuffer decoderInputBuffer;

  private final SonicAudioProcessor sonicAudioProcessor;
  private final SpeedProvider speedProvider;

  private final DecoderInputBuffer encoderInputBuffer;
  private final DecoderInputBuffer encoderOutputBuffer;

  private final Transformation transformation;
  private final int rendererIndex;

  private @MonotonicNonNull AudioFormat encoderInputAudioFormat;
  private @MonotonicNonNull MediaCodecAdapterWrapper encoder;
  private long nextEncoderInputBufferTimeUs;
  private long encoderBufferDurationRemainder;

  private ByteBuffer sonicOutputBuffer;
  private boolean drainingSonicForSpeedChange;
  private float currentSpeed;

  public AudioSamplePipeline(
      Format decoderInputFormat, Transformation transformation, int rendererIndex)
      throws ExoPlaybackException {
    this.decoderInputFormat = decoderInputFormat;
    this.transformation = transformation;
    this.rendererIndex = rendererIndex;
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    sonicAudioProcessor = new SonicAudioProcessor();
    sonicOutputBuffer = AudioProcessor.EMPTY_BUFFER;
    speedProvider = new SegmentSpeedProvider(decoderInputFormat);
    currentSpeed = speedProvider.getSpeed(0);
    try {
      this.decoder = MediaCodecAdapterWrapper.createForAudioDecoding(decoderInputFormat);
    } catch (IOException e) {
      // TODO (internal b/184262323): Assign an adequate error code.
      throw ExoPlaybackException.createForRenderer(
          e,
          TAG,
          rendererIndex,
          decoderInputFormat,
          /* rendererFormatSupport= */ C.FORMAT_HANDLED,
          /* isRecoverable= */ false,
          PlaybackException.ERROR_CODE_UNSPECIFIED);
    }
  }

  @Override
  public void release() {
    sonicAudioProcessor.reset();
    decoder.release();
    if (encoder != null) {
      encoder.release();
    }
  }

  @Override
  public boolean processData() throws ExoPlaybackException {
    if (!ensureEncoderAndAudioProcessingConfigured()) {
      return false;
    }
    if (sonicAudioProcessor.isActive()) {
      return feedEncoderFromSonic() || feedSonicFromDecoder();
    } else {
      return feedEncoderFromDecoder();
    }
  }

  @Override
  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() {
    return decoder.maybeDequeueInputBuffer(decoderInputBuffer) ? decoderInputBuffer : null;
  }

  @Override
  public void queueInputBuffer() {
    decoder.queueInputBuffer(decoderInputBuffer);
  }

  @Override
  @Nullable
  public Format getOutputFormat() {
    return encoder != null ? encoder.getOutputFormat() : null;
  }

  @Override
  public boolean isEnded() {
    return encoder != null && encoder.isEnded();
  }

  @Override
  @Nullable
  public DecoderInputBuffer getOutputBuffer() {
    if (encoder != null) {
      encoderOutputBuffer.data = encoder.getOutputBuffer();
      if (encoderOutputBuffer.data != null) {
        encoderOutputBuffer.timeUs = checkNotNull(encoder.getOutputBufferInfo()).presentationTimeUs;
        return encoderOutputBuffer;
      }
    }
    return null;
  }

  @Override
  public void releaseOutputBuffer() {
    checkStateNotNull(encoder).releaseOutputBuffer();
  }

  /**
   * Attempts to pass decoder output data to the encoder, and returns whether it may be possible to
   * pass more data immediately by calling this method again.
   */
  @RequiresNonNull({"encoderInputAudioFormat", "encoder"})
  private boolean feedEncoderFromDecoder() {
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
    if (isSpeedChanging(checkNotNull(decoder.getOutputBufferInfo()))) {
      flushSonicAndSetSpeed(currentSpeed);
      return false;
    }
    feedEncoder(decoderOutputBuffer);
    if (!decoderOutputBuffer.hasRemaining()) {
      decoder.releaseOutputBuffer();
    }
    return true;
  }

  /**
   * Attempts to pass audio processor output data to the encoder, and returns whether it may be
   * possible to pass more data immediately by calling this method again.
   */
  @RequiresNonNull({"encoderInputAudioFormat", "encoder"})
  private boolean feedEncoderFromSonic() {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (!sonicOutputBuffer.hasRemaining()) {
      sonicOutputBuffer = sonicAudioProcessor.getOutput();
      if (!sonicOutputBuffer.hasRemaining()) {
        if (decoder.isEnded() && sonicAudioProcessor.isEnded()) {
          queueEndOfStreamToEncoder();
        }
        return false;
      }
    }

    feedEncoder(sonicOutputBuffer);
    return true;
  }

  /**
   * Attempts to process decoder output data, and returns whether it may be possible to process more
   * data immediately by calling this method again.
   */
  private boolean feedSonicFromDecoder() {
    if (drainingSonicForSpeedChange) {
      if (sonicAudioProcessor.isEnded() && !sonicOutputBuffer.hasRemaining()) {
        flushSonicAndSetSpeed(currentSpeed);
        drainingSonicForSpeedChange = false;
      }
      return false;
    }

    // Sonic invalidates any previous output buffer when more input is queued, so we don't queue if
    // there is output still to be processed.
    if (sonicOutputBuffer.hasRemaining()) {
      return false;
    }

    if (decoder.isEnded()) {
      sonicAudioProcessor.queueEndOfStream();
      return false;
    }
    checkState(!sonicAudioProcessor.isEnded());

    @Nullable ByteBuffer decoderOutputBuffer = decoder.getOutputBuffer();
    if (decoderOutputBuffer == null) {
      return false;
    }
    if (isSpeedChanging(checkNotNull(decoder.getOutputBufferInfo()))) {
      sonicAudioProcessor.queueEndOfStream();
      drainingSonicForSpeedChange = true;
      return false;
    }
    sonicAudioProcessor.queueInput(decoderOutputBuffer);
    if (!decoderOutputBuffer.hasRemaining()) {
      decoder.releaseOutputBuffer();
    }
    return true;
  }

  /**
   * Feeds as much data as possible between the current position and limit of the specified {@link
   * ByteBuffer} to the encoder, and advances its position by the number of bytes fed.
   */
  @RequiresNonNull({"encoder", "encoderInputAudioFormat"})
  private void feedEncoder(ByteBuffer inputBuffer) {
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

  @RequiresNonNull("encoder")
  private void queueEndOfStreamToEncoder() {
    checkState(checkNotNull(encoderInputBuffer.data).position() == 0);
    encoderInputBuffer.timeUs = nextEncoderInputBufferTimeUs;
    encoderInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    encoderInputBuffer.flip();
    // Queuing EOS should only occur with an empty buffer.
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  /**
   * Attempts to configure the {@link #encoder} and Sonic (if applicable), if they have not been
   * configured yet, and returns whether they have been configured.
   */
  @EnsuresNonNullIf(
      expression = {"encoder", "encoderInputAudioFormat"},
      result = true)
  private boolean ensureEncoderAndAudioProcessingConfigured() throws ExoPlaybackException {
    if (encoder != null && encoderInputAudioFormat != null) {
      return true;
    }
    @Nullable Format decoderOutputFormat = decoder.getOutputFormat();
    if (decoderOutputFormat == null) {
      return false;
    }
    AudioFormat outputAudioFormat =
        new AudioFormat(
            decoderOutputFormat.sampleRate,
            decoderOutputFormat.channelCount,
            decoderOutputFormat.pcmEncoding);
    if (transformation.flattenForSlowMotion) {
      try {
        outputAudioFormat = sonicAudioProcessor.configure(outputAudioFormat);
        flushSonicAndSetSpeed(currentSpeed);
      } catch (AudioProcessor.UnhandledAudioFormatException e) {
        // TODO(internal b/192864511): Assign an adequate error code.
        throw createRendererException(e, PlaybackException.ERROR_CODE_UNSPECIFIED);
      }
    }
    String audioMimeType =
        transformation.audioMimeType == null
            ? decoderInputFormat.sampleMimeType
            : transformation.audioMimeType;
    try {
      encoder =
          MediaCodecAdapterWrapper.createForAudioEncoding(
              new Format.Builder()
                  .setSampleMimeType(audioMimeType)
                  .setSampleRate(outputAudioFormat.sampleRate)
                  .setChannelCount(outputAudioFormat.channelCount)
                  .setAverageBitrate(DEFAULT_ENCODER_BITRATE)
                  .build());
    } catch (IOException e) {
      // TODO(internal b/192864511): Assign an adequate error code.
      throw createRendererException(e, PlaybackException.ERROR_CODE_UNSPECIFIED);
    }
    encoderInputAudioFormat = outputAudioFormat;
    return true;
  }

  private boolean isSpeedChanging(BufferInfo bufferInfo) {
    if (!transformation.flattenForSlowMotion) {
      return false;
    }
    float newSpeed = speedProvider.getSpeed(bufferInfo.presentationTimeUs);
    boolean speedChanging = newSpeed != currentSpeed;
    currentSpeed = newSpeed;
    return speedChanging;
  }

  private void flushSonicAndSetSpeed(float speed) {
    sonicAudioProcessor.setSpeed(speed);
    sonicAudioProcessor.setPitch(speed);
    sonicAudioProcessor.flush();
  }

  private ExoPlaybackException createRendererException(Throwable cause, int errorCode) {
    return ExoPlaybackException.createForRenderer(
        cause,
        TAG,
        rendererIndex,
        decoderInputFormat,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false,
        errorCode);
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
