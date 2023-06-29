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

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT;
import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.min;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessingPipeline;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.ChannelMixingAudioProcessor;
import com.google.android.exoplayer2.audio.ChannelMixingMatrix;
import com.google.android.exoplayer2.audio.SonicAudioProcessor;
import com.google.android.exoplayer2.audio.SpeedChangingAudioProcessor;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NullableType;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to process, re-encode and mux raw audio samples.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class AudioSamplePipeline extends SamplePipeline {

  private static final int MAX_INPUT_BUFFER_COUNT = 10;
  private static final int DEFAULT_ENCODER_BITRATE = 128 * 1024;

  private final SilentAudioGenerator silentAudioGenerator;
  private final Queue<DecoderInputBuffer> availableInputBuffers;
  private final Queue<DecoderInputBuffer> pendingInputBuffers;
  private final Codec encoder;
  private final AudioFormat encoderInputAudioFormat;
  private final DecoderInputBuffer encoderInputBuffer;
  private final DecoderInputBuffer encoderOutputBuffer;
  private final AtomicReference<@NullableType Pair<EditedMediaItem, @NullableType Format>>
      pendingMediaItem;
  private boolean receivedFirstMediaItemCallback;
  private AudioProcessingPipeline audioProcessingPipeline;
  private long encoderTotalInputBytes;

  private volatile boolean queueEndOfStreamAfterSilence;

  // TODO(b/260618558): Move silent audio generation upstream of this component.
  public AudioSamplePipeline(
      Format firstAssetLoaderInputFormat,
      Format firstPipelineInputFormat,
      TransformationRequest transformationRequest,
      EditedMediaItem firstEditedMediaItem,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      FallbackListener fallbackListener)
      throws ExportException {
    super(firstAssetLoaderInputFormat, muxerWrapper);
    checkArgument(firstPipelineInputFormat.pcmEncoding != Format.NO_VALUE);

    availableInputBuffers = new ConcurrentLinkedDeque<>();
    ByteBuffer emptyBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());
    for (int i = 0; i < MAX_INPUT_BUFFER_COUNT; i++) {
      DecoderInputBuffer inputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT);
      inputBuffer.data = emptyBuffer;
      availableInputBuffers.add(inputBuffer);
    }
    pendingInputBuffers = new ConcurrentLinkedDeque<>();
    encoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
    pendingMediaItem = new AtomicReference<>();
    AudioFormat inputAudioFormat = new AudioFormat(firstPipelineInputFormat);
    silentAudioGenerator = new SilentAudioGenerator(inputAudioFormat);
    audioProcessingPipeline =
        configureProcessing(
            /* editedMediaItem= */ firstEditedMediaItem,
            /* trackFormat= */ firstPipelineInputFormat,
            /* inputAudioFormat= */ inputAudioFormat,
            /* requiredOutputAudioFormat= */ AudioFormat.NOT_SET);
    AudioFormat outputAudioFormat = audioProcessingPipeline.getOutputAudioFormat();
    checkState(!outputAudioFormat.equals(AudioFormat.NOT_SET));

    encoderInputAudioFormat = outputAudioFormat;
    Format requestedEncoderFormat =
        new Format.Builder()
            .setSampleMimeType(
                transformationRequest.audioMimeType != null
                    ? transformationRequest.audioMimeType
                    : checkNotNull(firstAssetLoaderInputFormat.sampleMimeType))
            .setSampleRate(encoderInputAudioFormat.sampleRate)
            .setChannelCount(encoderInputAudioFormat.channelCount)
            .setPcmEncoding(encoderInputAudioFormat.encoding)
            .setAverageBitrate(DEFAULT_ENCODER_BITRATE)
            .build();

    encoder =
        encoderFactory.createForAudioEncoding(
            requestedEncoderFormat
                .buildUpon()
                .setSampleMimeType(
                    findSupportedMimeTypeForEncoderAndMuxer(
                        requestedEncoderFormat,
                        muxerWrapper.getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO)))
                .build());

    fallbackListener.onTransformationRequestFinalized(
        createFallbackTransformationRequest(
            transformationRequest,
            requestedEncoderFormat,
            /* actualFormat= */ encoder.getConfigurationFormat()));
  }

  @Override
  public void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format trackFormat,
      boolean isLast) {
    if (trackFormat == null) {
      checkState(
          durationUs != C.TIME_UNSET,
          "Could not generate silent audio because duration is unknown.");
      silentAudioGenerator.addSilence(durationUs);
      if (isLast) {
        queueEndOfStreamAfterSilence = true;
      }
    } else {
      checkState(MimeTypes.isAudio(trackFormat.sampleMimeType));
      checkState(trackFormat.pcmEncoding != Format.NO_VALUE);
    }

    if (!receivedFirstMediaItemCallback) {
      receivedFirstMediaItemCallback = true;
      return;
    }
    pendingMediaItem.set(Pair.create(editedMediaItem, trackFormat));
  }

  @Override
  @Nullable
  public DecoderInputBuffer getInputBuffer() {
    if (shouldGenerateSilence() || pendingMediaItem.get() != null) {
      return null;
    }
    return availableInputBuffers.peek();
  }

  @Override
  public boolean queueInputBuffer() {
    checkState(pendingMediaItem.get() == null);
    DecoderInputBuffer inputBuffer = availableInputBuffers.remove();
    pendingInputBuffers.add(inputBuffer);
    return true;
  }

  @Override
  public void release() {
    audioProcessingPipeline.reset();
    encoder.release();
  }

  @Override
  protected boolean processDataUpToMuxer() throws ExportException {
    if (!audioProcessingPipeline.isOperational()) {
      return feedEncoderFromInput();
    }

    return feedEncoderFromProcessingPipeline() || feedProcessingPipelineFromInput();
  }

  @Override
  @Nullable
  protected Format getMuxerInputFormat() throws ExportException {
    return encoder.getOutputFormat();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() throws ExportException {
    encoderOutputBuffer.data = encoder.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    encoderOutputBuffer.timeUs = checkNotNull(encoder.getOutputBufferInfo()).presentationTimeUs;
    encoderOutputBuffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    return encoderOutputBuffer;
  }

  @Override
  protected void releaseMuxerInputBuffer() throws ExportException {
    encoder.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return encoder.isEnded();
  }

  /**
   * Reconfigures audio processing based on the pending {@linkplain #onMediaItemChanged media item
   * change}.
   *
   * <p>Before reconfiguration, all pending buffers must be fully processed and drained to the
   * encoder, however end of stream buffers should be handled so the encoder is not {@link
   * #queueEndOfStreamToEncoder() queued end of stream}.
   */
  private void reconfigureProcessingForPendingMediaItem() throws ExportException {
    Pair<EditedMediaItem, @NullableType Format> pendingChange =
        checkStateNotNull(pendingMediaItem.get());
    AudioFormat pendingAudioFormat =
        pendingChange.second != null
            ? new AudioFormat(pendingChange.second)
            : silentAudioGenerator.audioFormat;
    audioProcessingPipeline =
        configureProcessing(
            /* editedMediaItem= */ pendingChange.first,
            /* trackFormat= */ pendingChange.second,
            /* inputAudioFormat= */ pendingAudioFormat,
            /* requiredOutputAudioFormat= */ encoderInputAudioFormat);
    pendingMediaItem.set(null);
  }

  /**
   * Attempts to pass input data to the encoder.
   *
   * @return Whether the {@link AudioSamplePipeline} may be able to continue processing data.
   */
  private boolean feedEncoderFromInput() throws ExportException {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    if (shouldGenerateSilence()) {
      feedEncoder(silentAudioGenerator.getBuffer());
      return true;
    }

    if (pendingInputBuffers.isEmpty()) {
      if (pendingMediaItem.get() != null) {
        reconfigureProcessingForPendingMediaItem();
        return true;
      }
      // Only read volatile variable queueEndOfStreamAfterSilence if there is a chance that end of
      // stream should be queued.
      if (!silentAudioGenerator.hasRemaining() && queueEndOfStreamAfterSilence) {
        queueEndOfStreamToEncoder();
      }
      return false;
    }

    DecoderInputBuffer pendingInputBuffer = pendingInputBuffers.element();
    if (pendingInputBuffer.isEndOfStream()) {
      if (pendingMediaItem.get() == null) {
        queueEndOfStreamToEncoder();
      }
      removePendingInputBuffer();
      return false;
    }

    ByteBuffer inputData = checkNotNull(pendingInputBuffer.data);
    feedEncoder(inputData);
    if (!inputData.hasRemaining()) {
      removePendingInputBuffer();
    }
    return true;
  }

  /**
   * Attempts to feed audio processor output data to the encoder.
   *
   * @return Whether the {@link AudioSamplePipeline} may be able to continue processing data.
   */
  private boolean feedEncoderFromProcessingPipeline() throws ExportException {
    if (!encoder.maybeDequeueInputBuffer(encoderInputBuffer)) {
      return false;
    }

    ByteBuffer processingPipelineOutputBuffer = audioProcessingPipeline.getOutput();
    if (!processingPipelineOutputBuffer.hasRemaining()) {
      if (audioProcessingPipeline.isEnded()) {
        if (pendingMediaItem.get() != null) {
          reconfigureProcessingForPendingMediaItem();
          return true;
        }
        queueEndOfStreamToEncoder();
      }
      return false;
    }

    feedEncoder(processingPipelineOutputBuffer);
    return true;
  }

  /**
   * Attempts to feed input data to the {@link AudioProcessingPipeline}.
   *
   * @return Whether the {@link AudioSamplePipeline} may be able to continue processing data.
   */
  private boolean feedProcessingPipelineFromInput() {
    if (shouldGenerateSilence()) {
      ByteBuffer inputData = silentAudioGenerator.getBuffer();
      audioProcessingPipeline.queueInput(inputData);
      return !inputData.hasRemaining();
    }

    if (pendingInputBuffers.isEmpty()) {
      // Only read volatile variable queueEndOfStreamAfterSilence if there is a chance that end of
      // stream should be queued.
      if (pendingMediaItem.get() != null
          || (!silentAudioGenerator.hasRemaining() && queueEndOfStreamAfterSilence)) {
        audioProcessingPipeline.queueEndOfStream();
      }
      return false;
    }

    DecoderInputBuffer pendingInputBuffer = pendingInputBuffers.element();
    if (pendingInputBuffer.isEndOfStream()) {
      audioProcessingPipeline.queueEndOfStream();
      removePendingInputBuffer();
      return false;
    }

    ByteBuffer inputData = checkNotNull(pendingInputBuffer.data);
    audioProcessingPipeline.queueInput(inputData);
    if (inputData.hasRemaining()) {
      return false;
    }

    removePendingInputBuffer();
    return true;
  }

  private void removePendingInputBuffer() {
    DecoderInputBuffer inputBuffer = pendingInputBuffers.remove();
    inputBuffer.clear();
    inputBuffer.timeUs = 0;
    availableInputBuffers.add(inputBuffer);
  }

  /**
   * Feeds as much data as possible between the current position and limit of the specified {@link
   * ByteBuffer} to the encoder, and advances its position by the number of bytes fed.
   */
  private void feedEncoder(ByteBuffer inputBuffer) throws ExportException {
    ByteBuffer encoderInputBufferData = checkNotNull(encoderInputBuffer.data);
    int bufferLimit = inputBuffer.limit();
    inputBuffer.limit(min(bufferLimit, inputBuffer.position() + encoderInputBufferData.capacity()));
    encoderInputBufferData.put(inputBuffer);
    encoderInputBuffer.timeUs = getOutputAudioDurationUs();
    encoderTotalInputBytes += encoderInputBufferData.position();
    encoderInputBuffer.setFlags(0);
    encoderInputBuffer.flip();
    inputBuffer.limit(bufferLimit);
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  private void queueEndOfStreamToEncoder() throws ExportException {
    checkState(checkNotNull(encoderInputBuffer.data).position() == 0);
    encoderInputBuffer.timeUs = getOutputAudioDurationUs();
    encoderInputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM);
    encoderInputBuffer.flip();
    // Queuing EOS should only occur with an empty buffer.
    encoder.queueInputBuffer(encoderInputBuffer);
  }

  @Pure
  private static TransformationRequest createFallbackTransformationRequest(
      TransformationRequest transformationRequest, Format requestedFormat, Format actualFormat) {
    // TODO(b/259570024): Consider including bitrate and other audio characteristics in the revised
    //  fallback design.
    if (Util.areEqual(requestedFormat.sampleMimeType, actualFormat.sampleMimeType)) {
      return transformationRequest;
    }
    return transformationRequest.buildUpon().setAudioMimeType(actualFormat.sampleMimeType).build();
  }

  private long getOutputAudioDurationUs() {
    long totalFramesWritten = encoderTotalInputBytes / encoderInputAudioFormat.bytesPerFrame;
    return (totalFramesWritten * C.MICROS_PER_SECOND) / encoderInputAudioFormat.sampleRate;
  }

  private boolean shouldGenerateSilence() {
    return silentAudioGenerator.hasRemaining() && pendingInputBuffers.isEmpty();
  }

  private static AudioProcessingPipeline configureProcessing(
      EditedMediaItem editedMediaItem,
      @Nullable Format trackFormat,
      AudioFormat inputAudioFormat,
      AudioFormat requiredOutputAudioFormat)
      throws ExportException {
    ImmutableList.Builder<AudioProcessor> audioProcessors = new ImmutableList.Builder<>();
    if (editedMediaItem.flattenForSlowMotion
        && trackFormat != null
        && trackFormat.metadata != null) {
      audioProcessors.add(
          new SpeedChangingAudioProcessor(new SegmentSpeedProvider(trackFormat.metadata)));
    }
    audioProcessors.addAll(editedMediaItem.effects.audioProcessors);
    // Ensure the output from APP matches what the encoder is configured to receive.
    if (!requiredOutputAudioFormat.equals(AudioFormat.NOT_SET)) {
      SonicAudioProcessor sampleRateChanger = new SonicAudioProcessor();
      sampleRateChanger.setOutputSampleRateHz(requiredOutputAudioFormat.sampleRate);
      audioProcessors.add(sampleRateChanger);

      // TODO(b/262706549): Handle channel mixing with AudioMixer.
      if (requiredOutputAudioFormat.channelCount <= 2) {
        // ChannelMixingMatrix.create only has defaults for mono/stereo input/output.
        ChannelMixingAudioProcessor channelCountChanger = new ChannelMixingAudioProcessor();
        channelCountChanger.putChannelMixingMatrix(
            ChannelMixingMatrix.create(
                /* inputChannelCount= */ 1, requiredOutputAudioFormat.channelCount));
        channelCountChanger.putChannelMixingMatrix(
            ChannelMixingMatrix.create(
                /* inputChannelCount= */ 2, requiredOutputAudioFormat.channelCount));
        audioProcessors.add(channelCountChanger);
      }
    }

    AudioProcessingPipeline audioProcessingPipeline =
        new AudioProcessingPipeline(audioProcessors.build());
    try {
      AudioFormat outputAudioFormat = audioProcessingPipeline.configure(inputAudioFormat);
      if (!requiredOutputAudioFormat.equals(AudioFormat.NOT_SET)
          && !outputAudioFormat.equals(requiredOutputAudioFormat)) {
        throw new AudioProcessor.UnhandledAudioFormatException(
            "Audio format can not be modified to match existing downstream format",
            inputAudioFormat);
      }
    } catch (AudioProcessor.UnhandledAudioFormatException unhandledAudioFormatException) {
      throw ExportException.createForAudioProcessing(
          unhandledAudioFormatException, inputAudioFormat);
    }

    audioProcessingPipeline.flush();
    return audioProcessingPipeline;
  }
}
