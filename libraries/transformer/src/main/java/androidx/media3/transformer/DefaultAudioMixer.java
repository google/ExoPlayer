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
package androidx.media3.transformer;

import static androidx.media3.common.audio.AudioProcessor.EMPTY_BUFFER;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.contains;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.util.SparseArray;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioMixingUtil;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException;
import androidx.media3.common.audio.ChannelMixingMatrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** An {@link AudioMixer} that incrementally mixes source audio into a fixed size mixing buffer. */
@UnstableApi
public final class DefaultAudioMixer implements AudioMixer {

  /** An {@link AudioMixer.Factory} implementation for {@link DefaultAudioMixer} instances. */
  public static final class Factory implements AudioMixer.Factory {
    private final boolean outputSilenceWithNoSources;

    /**
     * Creates an instance that does not {@linkplain #getOutput() output} silence when there are no
     * {@linkplain #addSource sources}.
     */
    public Factory() {
      this(/* outputSilenceWithNoSources= */ false);
    }

    /**
     * Creates an instance.
     *
     * @param outputSilenceWithNoSources Whether to {@linkplain #getOutput() output} silence when
     *     there are no {@linkplain #addSource sources}.
     */
    public Factory(boolean outputSilenceWithNoSources) {
      this.outputSilenceWithNoSources = outputSilenceWithNoSources;
    }

    @Override
    public DefaultAudioMixer create() {
      return new DefaultAudioMixer(outputSilenceWithNoSources);
    }
  }

  // TODO(b/290002438, b/276734854): Improve buffer management & determine best default size.
  private static final int DEFAULT_BUFFER_SIZE_MS = 500;

  private final SparseArray<SourceInfo> sources;
  private int nextSourceId;
  private AudioFormat outputAudioFormat;
  private int bufferSizeFrames;
  private MixingBuffer[] mixingBuffers;
  private long mixerStartTimeUs;

  /** Limit (in frames) of source audio that can be queued, relative to the mixer start. */
  private long inputLimit;

  /** Position (in frames) of the next output audio frame, relative to the mixer start. */
  private long outputPosition;

  /** Position (in frames) of the mixer end point, relative to the mixer start. */
  private long endPosition;

  /**
   * Largest position (in frames) of {@link #removeSource(int) removed} sources, relative to the
   * mixer start.
   */
  private long maxPositionOfRemovedSources;

  private DefaultAudioMixer(boolean outputSilenceWithNoSources) {
    sources = new SparseArray<>();
    outputAudioFormat = AudioFormat.NOT_SET;
    bufferSizeFrames = C.LENGTH_UNSET;
    mixingBuffers = new MixingBuffer[0];
    mixerStartTimeUs = C.TIME_UNSET;
    inputLimit = C.LENGTH_UNSET;
    endPosition = Long.MAX_VALUE;
    if (outputSilenceWithNoSources) {
      maxPositionOfRemovedSources = Long.MAX_VALUE;
    }
  }

  @Override
  public void configure(AudioFormat outputAudioFormat, int bufferSizeMs, long startTimeUs)
      throws UnhandledAudioFormatException {
    checkState(
        this.outputAudioFormat.equals(AudioFormat.NOT_SET), "Audio mixer already configured.");

    if (bufferSizeMs == C.LENGTH_UNSET) {
      bufferSizeMs = DEFAULT_BUFFER_SIZE_MS;
    }
    checkArgument(bufferSizeMs > 0);

    if (!AudioMixingUtil.canMix(outputAudioFormat)) {
      throw new UnhandledAudioFormatException(
          "Can not mix to this AudioFormat.", outputAudioFormat);
    }
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
    return AudioMixingUtil.canMix(sourceFormat, outputAudioFormat);
  }

  @Override
  public int addSource(AudioFormat sourceFormat, long startTimeUs)
      throws UnhandledAudioFormatException {
    checkStateIsConfigured();
    if (!supportsSourceAudioFormat(sourceFormat)) {
      throw new UnhandledAudioFormatException(
          "Can not add source. MixerFormat=" + outputAudioFormat, sourceFormat);
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
  public boolean hasSource(int sourceId) {
    checkStateIsConfigured();
    return contains(sources, sourceId);
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
    maxPositionOfRemovedSources =
        max(maxPositionOfRemovedSources, getSourceById(sourceId).position);
    sources.delete(sourceId);
  }

  @Override
  public void queueInput(int sourceId, ByteBuffer sourceBuffer) {
    checkStateIsConfigured();
    if (!sourceBuffer.hasRemaining()) {
      return;
    }

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
          mixingBuffer.buffer,
          outputAudioFormat);
      mixingBuffer.buffer.reset();

      if (source.position == newSourcePosition) {
        return;
      }
    }
  }

  @Override
  public ByteBuffer getOutput() {
    checkStateIsConfigured();
    if (isEnded()) {
      return EMPTY_BUFFER;
    }

    long minSourcePosition = endPosition;
    if (sources.size() == 0) {
      minSourcePosition = min(minSourcePosition, maxPositionOfRemovedSources);
    }

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
    return outputPosition >= endPosition
        || (outputPosition >= maxPositionOfRemovedSources && sources.size() == 0);
  }

  @Override
  public void reset() {
    sources.clear();
    nextSourceId = 0;
    outputAudioFormat = AudioFormat.NOT_SET;
    bufferSizeFrames = C.LENGTH_UNSET;
    mixingBuffers = new MixingBuffer[0];
    mixerStartTimeUs = C.TIME_UNSET;
    inputLimit = C.LENGTH_UNSET;
    outputPosition = 0;
    endPosition = Long.MAX_VALUE;
  }

  private void checkStateIsConfigured() {
    checkState(!outputAudioFormat.equals(AudioFormat.NOT_SET), "Audio mixer is not configured.");
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
    checkState(contains(sources, sourceId), "Source not found.");
    return sources.get(sourceId);
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
        ByteBuffer mixingBuffer,
        AudioFormat mixingAudioFormat) {
      checkArgument(newPosition >= position);
      int framesToMix = (int) (newPosition - position);
      AudioMixingUtil.mix(
          sourceBuffer,
          audioFormat,
          mixingBuffer,
          mixingAudioFormat,
          channelMixingMatrix,
          framesToMix,
          /* accumulate= */ true);
      position = newPosition;
    }
  }
}
