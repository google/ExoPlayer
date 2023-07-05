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
import static java.lang.Math.min;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.AudioProcessor.AudioFormat;
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException;
import com.google.android.exoplayer2.audio.ChannelMixingMatrix;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioMixer} that incrementally mixes source audio into a fixed size mixing buffer.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class AudioMixerImpl implements AudioMixer {

  private static final ByteBuffer EMPTY_BUFFER =
      ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

  private final SparseArray<SourceInfo> sources;
  private int nextSourceId;
  private AudioFormat outputAudioFormat;
  @Nullable private AudioMixingAlgorithm mixingAlgorithm;
  private int bufferSizeFrames;
  private MixingBuffer[] mixingBuffers;
  private long mixerStartTimeUs;

  /** Limit (in frames) of source audio that can be queued, relative to the mixer start. */
  private long inputLimit;

  /** Position (in frames) of the next output audio frame, relative to the mixer start. */
  private long outputPosition;

  /** Position (in frames) of the mixer end point, relative to the mixer start. */
  private long endPosition;

  public AudioMixerImpl() {
    sources = new SparseArray<>();
    outputAudioFormat = AudioFormat.NOT_SET;
    bufferSizeFrames = C.LENGTH_UNSET;
    mixingBuffers = new MixingBuffer[0];
    mixerStartTimeUs = C.TIME_UNSET;
    inputLimit = C.LENGTH_UNSET;
    endPosition = Long.MAX_VALUE;
  }

  @Override
  public void configure(AudioFormat outputAudioFormat, int bufferSizeMs, long startTimeUs)
      throws UnhandledAudioFormatException {
    checkState(!isConfigured(), "Audio mixer already configured.");

    // Create algorithm first in case it throws.
    mixingAlgorithm = AudioMixingAlgorithm.create(outputAudioFormat);
    this.outputAudioFormat = outputAudioFormat;
    bufferSizeFrames = bufferSizeMs * outputAudioFormat.sampleRate / 1000;
    mixerStartTimeUs = startTimeUs;

    mixingBuffers =
        new MixingBuffer[] {allocateMixingBuffer(0), allocateMixingBuffer(bufferSizeFrames)};
    updateInputFrameLimit();
  }

  @Override
  public void setEndTimeUs(long endTimeUs) {
    checkStateIsConfigured();
    checkArgument(
        endTimeUs >= mixerStartTimeUs, "End time must be at least the configured start time.");

    endPosition =
        Util.scaleLargeTimestamp(
            endTimeUs - mixerStartTimeUs,
            /* multiplier= */ outputAudioFormat.sampleRate,
            /* divisor= */ C.MICROS_PER_SECOND);
    updateInputFrameLimit();
  }

  @Override
  public boolean supportsSourceAudioFormat(AudioFormat sourceFormat) {
    checkStateIsConfigured();
    return checkStateNotNull(mixingAlgorithm).supportsSourceAudioFormat(sourceFormat);
  }

  @Override
  public int addSource(AudioFormat sourceFormat, long startTimeUs)
      throws UnhandledAudioFormatException {
    checkStateIsConfigured();
    if (!supportsSourceAudioFormat(sourceFormat)) {
      throw new UnhandledAudioFormatException(sourceFormat);
    }

    long startFrameOffset =
        Util.scaleLargeTimestamp(
            startTimeUs - mixerStartTimeUs,
            /* multiplier= */ sourceFormat.sampleRate,
            /* divisor= */ C.MICROS_PER_SECOND);
    int sourceId = nextSourceId++;
    sources.append(
        sourceId,
        new SourceInfo(
            sourceFormat,
            ChannelMixingMatrix.create(sourceFormat.channelCount, outputAudioFormat.channelCount),
            startFrameOffset));

    return sourceId;
  }

  @Override
  public void setSourceVolume(int sourceId, float volume) {
    checkStateIsConfigured();
    checkArgument(volume >= 0f, "Volume must be non-negative.");

    SourceInfo source = getSourceById(sourceId);
    source.setVolume(volume);
  }

  @Override
  public void removeSource(int sourceId) {
    checkStateIsConfigured();
    sources.delete(sourceId);
  }

  @Override
  public void queueInput(int sourceId, ByteBuffer sourceBuffer) {
    checkStateIsConfigured();

    SourceInfo source = getSourceById(sourceId);
    if (source.position >= inputLimit) {
      return;
    }

    long newSourcePosition = min(source.getPositionAfterBuffer(sourceBuffer), inputLimit);
    if (source.getChannelMixingMatrix().isZero()) {
      // Fast path for silent sources that avoids mixing.
      source.discardTo(sourceBuffer, newSourcePosition);
      return;
    }

    if (source.position < outputPosition) {
      // Discard early frames.
      source.discardTo(sourceBuffer, min(newSourcePosition, outputPosition));
      if (source.position == newSourcePosition) {
        return;
      }
    }

    for (MixingBuffer mixingBuffer : mixingBuffers) {
      if (source.position >= mixingBuffer.limit) {
        continue;
      }

      int mixingBufferPositionOffset =
          (int) (source.position - mixingBuffer.position) * outputAudioFormat.bytesPerFrame;
      mixingBuffer.buffer.position(mixingBuffer.buffer.position() + mixingBufferPositionOffset);
      source.mixTo(
          sourceBuffer,
          min(newSourcePosition, mixingBuffer.limit),
          checkNotNull(mixingAlgorithm),
          mixingBuffer.buffer);
      mixingBuffer.buffer.reset();

      if (source.position == newSourcePosition) {
        return;
      }
    }
  }

  @Override
  public ByteBuffer getOutput() {
    checkStateIsConfigured();

    long minSourcePosition = endPosition;
    for (int i = 0; i < sources.size(); i++) {
      minSourcePosition = min(minSourcePosition, sources.valueAt(i).position);
    }

    if (minSourcePosition <= outputPosition) {
      return EMPTY_BUFFER;
    }

    MixingBuffer mixingBuffer = mixingBuffers[0];
    long newOutputPosition = min(minSourcePosition, mixingBuffer.limit);
    ByteBuffer outputBuffer = mixingBuffer.buffer.duplicate();
    outputBuffer
        .position((int) (outputPosition - mixingBuffer.position) * outputAudioFormat.bytesPerFrame)
        .limit((int) (newOutputPosition - mixingBuffer.position) * outputAudioFormat.bytesPerFrame);
    outputBuffer = outputBuffer.slice().order(ByteOrder.nativeOrder());

    if (newOutputPosition == mixingBuffer.limit) {
      // TODO(b/264926272): Generalize for >2 mixing buffers.
      mixingBuffers[0] = mixingBuffers[1];
      mixingBuffers[1] = allocateMixingBuffer(mixingBuffers[1].limit);
    }

    outputPosition = newOutputPosition;
    updateInputFrameLimit();

    return outputBuffer;
  }

  @Override
  public boolean isEnded() {
    checkStateIsConfigured();
    return outputPosition >= endPosition;
  }

  @Override
  public void reset() {
    sources.clear();
    nextSourceId = 0;
    outputAudioFormat = AudioFormat.NOT_SET;
    mixingAlgorithm = null;
    bufferSizeFrames = C.LENGTH_UNSET;
    mixingBuffers = new MixingBuffer[0];
    mixerStartTimeUs = C.TIME_UNSET;
    inputLimit = C.LENGTH_UNSET;
    outputPosition = 0;
    endPosition = Long.MAX_VALUE;
  }

  private boolean isConfigured() {
    return mixingAlgorithm != null;
  }

  private void checkStateIsConfigured() {
    checkState(isConfigured(), "Audio mixer is not configured.");
  }

  private MixingBuffer allocateMixingBuffer(long position) {
    ByteBuffer buffer =
        ByteBuffer.allocateDirect(bufferSizeFrames * outputAudioFormat.bytesPerFrame)
            .order(ByteOrder.nativeOrder());
    buffer.mark();
    return new MixingBuffer(buffer, position, position + bufferSizeFrames);
  }

  private void updateInputFrameLimit() {
    inputLimit = min(endPosition, outputPosition + bufferSizeFrames);
  }

  private SourceInfo getSourceById(int sourceId) {
    return checkStateNotNull(sources.get(sourceId), "Source not found.");
  }

  /** A buffer holding partially-mixed audio within an interval. */
  private static class MixingBuffer {
    public final ByteBuffer buffer;

    /** Position (in frames) of the first frame in {@code buffer} relative to the mixer start. */
    public final long position;

    /**
     * Position (in frames) one past the last frame in {@code buffer} relative to the mixer start.
     */
    public final long limit;

    public MixingBuffer(ByteBuffer buffer, long position, long limit) {
      this.buffer = buffer;
      this.position = position;
      this.limit = limit;
    }
  }

  /** Per-source information. */
  private static class SourceInfo {
    /**
     * Position (in frames) of the next source audio frame to be input by the source, relative to
     * the mixer start.
     *
     * <p>Note: The position can be negative if the source start time is less than the mixer start
     * time.
     */
    public long position;

    private final AudioFormat audioFormat;
    private final ChannelMixingMatrix baseChannelMixingMatrix;
    private ChannelMixingMatrix channelMixingMatrix;

    public SourceInfo(
        AudioFormat audioFormat,
        ChannelMixingMatrix baseChannelMixingMatrix,
        long startFrameOffset) {
      this.audioFormat = audioFormat;
      this.baseChannelMixingMatrix = baseChannelMixingMatrix;
      position = startFrameOffset;
      channelMixingMatrix = baseChannelMixingMatrix; // Volume = 1f.
    }

    public ChannelMixingMatrix getChannelMixingMatrix() {
      return channelMixingMatrix;
    }

    public void setVolume(float volume) {
      channelMixingMatrix = baseChannelMixingMatrix.scaleBy(volume);
    }

    /** Returns the position of the next audio frame after {@code sourceBuffer}. */
    public long getPositionAfterBuffer(ByteBuffer sourceBuffer) {
      int sourceBufferFrameCount = sourceBuffer.remaining() / audioFormat.bytesPerFrame;
      return position + sourceBufferFrameCount;
    }

    /** Discards audio frames within {@code sourceBuffer} to the new source position. */
    public void discardTo(ByteBuffer sourceBuffer, long newPosition) {
      checkArgument(newPosition >= position);
      int framesToDiscard = (int) (newPosition - position);
      sourceBuffer.position(sourceBuffer.position() + framesToDiscard * audioFormat.bytesPerFrame);
      position = newPosition;
    }

    /** Mixes audio frames from {@code sourceBuffer} to the new source position. */
    public void mixTo(
        ByteBuffer sourceBuffer,
        long newPosition,
        AudioMixingAlgorithm mixingAlgorithm,
        ByteBuffer mixingBuffer) {
      checkArgument(newPosition >= position);
      int framesToMix = (int) (newPosition - position);
      mixingAlgorithm.mix(
          sourceBuffer, audioFormat, channelMixingMatrix, framesToMix, mixingBuffer);
      position = newPosition;
    }
  }
}
