/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.Math.min;

import android.media.MediaCodec.BufferInfo;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

@RequiresApi(18)
/* package */ final class TransformerAudioRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TransformerAudioRenderer";
  private static final int DEFAULT_ENCODER_BITRATE = 128 * 1024;
  private static final float SPEED_UNSET = -1f;

  private final DecoderInputBuffer decoderInputBuffer;
  private final DecoderInputBuffer encoderInputBuffer;
  private final SonicAudioProcessor sonicAudioProcessor;

  @Nullable private MediaCodecAdapterWrapper decoder;
  @Nullable private MediaCodecAdapterWrapper encoder;
  @Nullable private SpeedProvider speedProvider;
  private @MonotonicNonNull Format decoderInputFormat;
  private @MonotonicNonNull AudioFormat encoderInputAudioFormat;

  private ByteBuffer sonicOutputBuffer;
  private long nextEncoderInputBufferTimeUs;
  private float currentSpeed;
  private boolean muxerWrapperTrackEnded;
  private boolean hasEncoderOutputFormat;
  private boolean drainingSonicForSpeedChange;

  public TransformerAudioRenderer(
      MuxerWrapper muxerWrapper, TransformerMediaClock mediaClock, Transformation transformation) {
    super(C.TRACK_TYPE_AUDIO, muxerWrapper, mediaClock, transformation);
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    sonicAudioProcessor = new SonicAudioProcessor();
    sonicOutputBuffer = AudioProcessor.EMPTY_BUFFER;
    nextEncoderInputBufferTimeUs = 0;
    currentSpeed = SPEED_UNSET;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public boolean isEnded() {
    return muxerWrapperTrackEnded;
  }

  @Override
  protected void onReset() {
    decoderInputBuffer.clear();
    decoderInputBuffer.data = null;
    encoderInputBuffer.clear();
    encoderInputBuffer.data = null;
    sonicAudioProcessor.reset();
    if (decoder != null) {
      decoder.release();
      decoder = null;
    }
    if (encoder != null) {
      encoder.release();
      encoder = null;
    }
    speedProvider = null;
    sonicOutputBuffer = AudioProcessor.EMPTY_BUFFER;
    nextEncoderInputBufferTimeUs = 0;
    currentSpeed = SPEED_UNSET;
    muxerWrapperTrackEnded = false;
    hasEncoderOutputFormat = false;
    drainingSonicForSpeedChange = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!isRendererStarted || isEnded()) {
      return;
    }

    if (ensureDecoderConfigured()) {
      MediaCodecAdapterWrapper decoder = this.decoder;
      if (ensureEncoderAndAudioProcessingConfigured()) {
        MediaCodecAdapterWrapper encoder = this.encoder;
        while (feedMuxerFromEncoder(encoder)) {}
        if (sonicAudioProcessor.isActive()) {
          while (feedEncoderFromSonic(decoder, encoder)) {}
          while (feedSonicFromDecoder(decoder)) {}
        } else {
          while (feedEncoderFromDecoder(decoder, encoder)) {}
        }
      }
      while (feedDecoderFromInput(decoder)) {}
    }
  }

  /**
   * Attempts to write encoder output data to the muxer, and returns whether it may be possible to
   * write more data immediately by calling this method again.
   */
  private boolean feedMuxerFromEncoder(MediaCodecAdapterWrapper encoder) {
    if (!hasEncoderOutputFormat) {
      @Nullable Format encoderOutputFormat = encoder.getOutputFormat();
      if (encoderOutputFormat == null) {
        return false;
      }
      hasEncoderOutputFormat = true;
      muxerWrapper.addTrackFormat(encoderOutputFormat);
    }

    if (encoder.isEnded()) {
      muxerWrapper.endTrack(getTrackType());
      muxerWrapperTrackEnded = true;
      return false;
    }
    @Nullable ByteBuffer encoderOutputBuffer = encoder.getOutputBuffer();
    if (encoderOutputBuffer == null) {
      return false;
    }
    BufferInfo encoderOutputBufferInfo = checkNotNull(encoder.getOutputBufferInfo());
    if (!muxerWrapper.writeSample(
        getTrackType(),
        encoderOutputBuffer,
        /* isKeyFrame= */ true,
        encoderOutputBufferInfo.presentationTimeUs)) {
      return false;
    }
    encoder.releaseOutputBuffer();
    return true;
  }

  /**
   * Attempts to pass decoder output data to the encoder, and returns whether it may be possible to
   * pass more data immediately by calling this method again.
   */
  @RequiresNonNull({"encoderInputAudioFormat"})
  private boolean feedEncoderFromDecoder(
      MediaCodecAdapterWrapper decoder, MediaCodecAdapterWrapper encoder) {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (decoder.isEnded()) {
      queueEndOfStreamToEncoder(encoder);
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
    feedEncoder(encoder, decoderOutputBuffer);
    if (!decoderOutputBuffer.hasRemaining()) {
      decoder.releaseOutputBuffer();
    }
    return true;
  }

  /**
   * Attempts to pass audio processor output data to the encoder, and returns whether it may be
   * possible to pass more data immediately by calling this method again.
   */
  @RequiresNonNull({"encoderInputAudioFormat"})
  private boolean feedEncoderFromSonic(
      MediaCodecAdapterWrapper decoder, MediaCodecAdapterWrapper encoder) {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (!sonicOutputBuffer.hasRemaining()) {
      sonicOutputBuffer = sonicAudioProcessor.getOutput();
      if (!sonicOutputBuffer.hasRemaining()) {
        if (decoder.isEnded() && sonicAudioProcessor.isEnded()) {
          queueEndOfStreamToEncoder(encoder);
        }
        return false;
      }
    }

    feedEncoder(encoder, sonicOutputBuffer);
    return true;
  }

  /**
   * Attempts to process decoder output data, and returns whether it may be possible to process more
   * data immediately by calling this method again.
   */
  private boolean feedSonicFromDecoder(MediaCodecAdapterWrapper decoder) {
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
   * Attempts to pass input data to the decoder, and returns whether it may be possible to pass more
   * data immediately by calling this method again.
   */
  private boolean feedDecoderFromInput(MediaCodecAdapterWrapper decoder) {
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    decoderInputBuffer.clear();
    @ReadDataResult
    int result = readSource(getFormatHolder(), decoderInputBuffer, /* readFlags= */ 0);
    switch (result) {
      case C.RESULT_BUFFER_READ:
        mediaClock.updateTimeForTrackType(getTrackType(), decoderInputBuffer.timeUs);
        decoderInputBuffer.timeUs -= streamOffsetUs;
        decoderInputBuffer.flip();
        decoder.queueInputBuffer(decoderInputBuffer);
        return !decoderInputBuffer.isEndOfStream();
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }

  /**
   * Feeds as much data as possible between the current position and limit of the specified {@link
   * ByteBuffer} to the encoder, and advances its position by the number of bytes fed.
   */
  @RequiresNonNull({"encoderInputAudioFormat"})
  private void feedEncoder(MediaCodecAdapterWrapper encoder, ByteBuffer inputBuffer) {
    ByteBuffer encoderInputBufferData = checkNotNull(encoderInputBuffer.data);
    int bufferLimit = inputBuffer.limit();
    inputBuffer.limit(min(bufferLimit, inputBuffer.position() + encoderInputBufferData.capacity()));
    encoderInputBufferData.put(inputBuffer);
    encoderInputBuffer.timeUs = nextEncoderInputBufferTimeUs;
    nextEncoderInputBufferTimeUs +=
        getBufferDurationUs(
            /* bytesWritten= */ encoderInputBufferData.position(),
            encoderInputAudioFormat.bytesPerFrame,
            encoderInputAudioFormat.sampleRate);
    encoderInputBuffer.setFlags(0);
    encoderInputBuffer.flip();
    inputBuffer.limit(bufferLimit);
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  private void queueEndOfStreamToEncoder(MediaCodecAdapterWrapper encoder) {
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
  @RequiresNonNull({"decoder", "decoderInputFormat"})
  @EnsuresNonNullIf(
      expression = {"encoder", "encoderInputAudioFormat"},
      result = true)
  private boolean ensureEncoderAndAudioProcessingConfigured() throws ExoPlaybackException {
    if (encoder != null && encoderInputAudioFormat != null) {
      return true;
    }
    MediaCodecAdapterWrapper decoder = this.decoder;
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

  /**
   * Attempts to configure the {@link #decoder} if it has not been configured yet, and returns
   * whether the decoder has been configured.
   */
  @EnsuresNonNullIf(
      expression = {"decoderInputFormat", "decoder"},
      result = true)
  private boolean ensureDecoderConfigured() throws ExoPlaybackException {
    if (decoder != null && decoderInputFormat != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult int result = readSource(formatHolder, decoderInputBuffer, FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    decoderInputFormat = checkNotNull(formatHolder.format);
    MediaCodecAdapterWrapper decoder;
    try {
      decoder = MediaCodecAdapterWrapper.createForAudioDecoding(decoderInputFormat);
    } catch (IOException e) {
      // TODO (internal b/184262323): Assign an adequate error code.
      throw createRendererException(e, PlaybackException.ERROR_CODE_UNSPECIFIED);
    }
    speedProvider = new SegmentSpeedProvider(decoderInputFormat);
    currentSpeed = speedProvider.getSpeed(0);
    this.decoder = decoder;
    return true;
  }

  private boolean isSpeedChanging(BufferInfo bufferInfo) {
    if (!transformation.flattenForSlowMotion) {
      return false;
    }
    float newSpeed = checkNotNull(speedProvider).getSpeed(bufferInfo.presentationTimeUs);
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
        getIndex(),
        decoderInputFormat,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false,
        errorCode);
  }

  private static long getBufferDurationUs(long bytesWritten, int bytesPerFrame, int sampleRate) {
    long framesWritten = bytesWritten / bytesPerFrame;
    return framesWritten * C.MICROS_PER_SECOND / sampleRate;
  }
}
