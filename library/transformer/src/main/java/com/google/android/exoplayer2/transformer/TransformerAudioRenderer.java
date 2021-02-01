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
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import java.io.IOException;
import java.nio.ByteBuffer;

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
  @Nullable private Format inputFormat;
  @Nullable private AudioFormat encoderInputAudioFormat;

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
    inputFormat = null;
    encoderInputAudioFormat = null;
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

    if (!setupDecoder()) {
      return;
    }
    setupEncoderAndMaybeSonic();

    while (drainEncoderToFeedMuxer()) {}
    if (sonicAudioProcessor.isActive()) {
      while (drainSonicToFeedEncoder()) {}
      while (drainDecoderToFeedSonic()) {}
    } else {
      while (drainDecoderToFeedEncoder()) {}
    }
    while (feedDecoderInputFromSource()) {}
  }

  /**
   * Attempts to write encoder output data to the muxer, and returns whether it may be possible to
   * write more data immediately by calling this method again.
   */
  private boolean drainEncoderToFeedMuxer() {
    MediaCodecAdapterWrapper encoder = checkNotNull(this.encoder);
    if (!hasEncoderOutputFormat) {
      // Dequeue output format change.
      encoder.maybeDequeueOutputBuffer();
      @Nullable Format encoderOutputFormat = encoder.getOutputFormat();
      if (encoderOutputFormat == null) {
        return false;
      }
      hasEncoderOutputFormat = true;
      muxerWrapper.addTrackFormat(encoderOutputFormat);
    }

    if (encoder.isEnded()) {
      // Encoder output stream ended and output is empty or null so end muxer track.
      muxerWrapper.endTrack(getTrackType());
      muxerWrapperTrackEnded = true;
      return false;
    }

    if (!encoder.maybeDequeueOutputBuffer()) {
      return false;
    }

    ByteBuffer encoderOutputBuffer = checkNotNull(encoder.getOutputBuffer());
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
  private boolean drainDecoderToFeedEncoder() {
    MediaCodecAdapterWrapper decoder = checkNotNull(this.decoder);
    MediaCodecAdapterWrapper encoder = checkNotNull(this.encoder);
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (decoder.isEnded()) {
      queueEndOfStreamToEncoder();
      return false;
    }

    if (!decoder.maybeDequeueOutputBuffer()) {
      return false;
    }

    if (isSpeedChanging(checkNotNull(decoder.getOutputBufferInfo()))) {
      flushSonicAndSetSpeed(currentSpeed);
      return false;
    }

    ByteBuffer decoderOutputBuffer = checkNotNull(decoder.getOutputBuffer());

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
  private boolean drainSonicToFeedEncoder() {
    MediaCodecAdapterWrapper encoder = checkNotNull(this.encoder);
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (!sonicOutputBuffer.hasRemaining()) {
      sonicOutputBuffer = sonicAudioProcessor.getOutput();
      if (!sonicOutputBuffer.hasRemaining()) {
        if (checkNotNull(decoder).isEnded() && sonicAudioProcessor.isEnded()) {
          queueEndOfStreamToEncoder();
        }
        return false;
      }
    }

    feedEncoder(sonicOutputBuffer);
    return true;
  }

  /**
   * Attempts to process decoder output audio, and returns whether it may be possible to process
   * more data immediately by calling this method again.
   */
  private boolean drainDecoderToFeedSonic() {
    MediaCodecAdapterWrapper decoder = checkNotNull(this.decoder);

    if (drainingSonicForSpeedChange) {
      if (!sonicAudioProcessor.isEnded()) {
        // Sonic needs draining, but has not fully drained yet.
        return false;
      }
      flushSonicAndSetSpeed(currentSpeed);
      drainingSonicForSpeedChange = false;
    }

    // Sonic invalidates the output buffer when more input is queued, so we don't queue if there is
    // output still to be processed.
    if (sonicOutputBuffer.hasRemaining()) {
      return false;
    }

    if (decoder.isEnded()) {
      sonicAudioProcessor.queueEndOfStream();
      return false;
    }

    checkState(!sonicAudioProcessor.isEnded());

    if (!decoder.maybeDequeueOutputBuffer()) {
      return false;
    }

    if (isSpeedChanging(checkNotNull(decoder.getOutputBufferInfo()))) {
      sonicAudioProcessor.queueEndOfStream();
      drainingSonicForSpeedChange = true;
      return false;
    }

    ByteBuffer decoderOutputBuffer = checkNotNull(decoder.getOutputBuffer());
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
  private boolean feedDecoderInputFromSource() {
    MediaCodecAdapterWrapper decoder = checkNotNull(this.decoder);
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    decoderInputBuffer.clear();
    @SampleStream.ReadDataResult
    int result = readSource(getFormatHolder(), decoderInputBuffer, /* formatRequired= */ false);
    switch (result) {
      case C.RESULT_BUFFER_READ:
        mediaClock.updateTimeForTrackType(getTrackType(), decoderInputBuffer.timeUs);
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
  private void feedEncoder(ByteBuffer inputBuffer) {
    AudioFormat encoderInputAudioFormat = checkNotNull(this.encoderInputAudioFormat);
    MediaCodecAdapterWrapper encoder = checkNotNull(this.encoder);
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

  private void queueEndOfStreamToEncoder() {
    MediaCodecAdapterWrapper encoder = checkNotNull(this.encoder);
    checkState(checkNotNull(encoderInputBuffer.data).position() == 0);
    encoderInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    encoderInputBuffer.flip();
    // Queuing EOS should only occur with an empty buffer.
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  /**
   * Configures the {@link #encoder} and Sonic (if applicable), if they have not been configured
   * yet.
   */
  private void setupEncoderAndMaybeSonic() throws ExoPlaybackException {
    if (encoder != null) {
      return;
    }
    // TODO(b/161127201): Use the decoder output format once the decoder is fed before setting up
    // the encoder.
    AudioFormat outputAudioFormat =
        new AudioFormat(
            checkNotNull(inputFormat).sampleRate, inputFormat.channelCount, C.ENCODING_PCM_16BIT);
    if (transformation.flattenForSlowMotion) {
      try {
        outputAudioFormat = sonicAudioProcessor.configure(outputAudioFormat);
        flushSonicAndSetSpeed(currentSpeed);
      } catch (AudioProcessor.UnhandledAudioFormatException e) {
        throw createRendererException(e);
      }
    }
    try {
      encoder =
          MediaCodecAdapterWrapper.createForAudioEncoding(
              new Format.Builder()
                  .setSampleMimeType(checkNotNull(inputFormat).sampleMimeType)
                  .setSampleRate(outputAudioFormat.sampleRate)
                  .setChannelCount(outputAudioFormat.channelCount)
                  .setAverageBitrate(DEFAULT_ENCODER_BITRATE)
                  .build());
    } catch (IOException e) {
      throw createRendererException(e);
    }
    encoderInputAudioFormat = outputAudioFormat;
  }

  /**
   * Attempts to configure the {@link #decoder} if it has not been configured yet, and returns
   * whether the decoder has been configured.
   */
  private boolean setupDecoder() throws ExoPlaybackException {
    if (decoder != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @SampleStream.ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* formatRequired= */ true);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    inputFormat = checkNotNull(formatHolder.format);
    try {
      decoder = MediaCodecAdapterWrapper.createForAudioDecoding(inputFormat);
    } catch (IOException e) {
      throw createRendererException(e);
    }
    speedProvider = new SegmentSpeedProvider(inputFormat);
    currentSpeed = speedProvider.getSpeed(0);
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

  private ExoPlaybackException createRendererException(Throwable cause) {
    return ExoPlaybackException.createForRenderer(
        cause, TAG, getIndex(), inputFormat, /* rendererFormatSupport= */ C.FORMAT_HANDLED);
  }

  private static long getBufferDurationUs(long bytesWritten, int bytesPerFrame, int sampleRate) {
    long framesWritten = bytesWritten / bytesPerFrame;
    return framesWritten * C.MICROS_PER_SECOND / sampleRate;
  }
}
